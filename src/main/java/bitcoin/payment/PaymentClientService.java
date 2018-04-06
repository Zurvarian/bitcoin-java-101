package bitcoin.payment;

import bitcoin.payment.dto.BroadcastPaymentDto;
import bitcoin.payment.dto.PaymentAckDto;
import bitcoin.payment.dto.PaymentDto;
import bitcoin.payment.dto.PaymentRequestDto;
import bitcoin.payment.dto.PaymentRequestUrl;
import bitcoin.transaction.dto.TransactionDto;
import bitcoin.transaction.dto.TransactionDto.TransactionInputDto;
import bitcoin.transaction.dto.TransactionDto.TransactionOutputDto;
import bitcoin.wallet.WalletRepository;
import com.google.protobuf.ByteString;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoin.protocols.payments.Protos.PaymentACK;
import org.bitcoin.protocols.payments.Protos.PaymentRequest;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.SendRequest;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.util.UUID;
import java.util.function.Function;

import static bitcoin.config.NetworkConfig.networkParameters;
import static io.vavr.API.Option;
import static io.vavr.API.Try;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;
import static org.bitcoinj.core.Address.fromBase58;
import static org.bitcoinj.core.Utils.HEX;
import static org.bitcoinj.protocols.payments.PaymentProtocol.createPayToAddressOutput;
import static org.bitcoinj.protocols.payments.PaymentProtocol.createPaymentMessage;
import static org.bitcoinj.protocols.payments.PaymentProtocol.verifyPaymentRequestPki;

@Component
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@Slf4j
public class PaymentClientService {

    PaymentClient paymentClient;

    KeyStore keyStore;

    WalletRepository walletRepository;

    PaymentRequestDto findPaymentRequest(PaymentRequestUrl paymentRequestUrl) {

        val paymentRequestResponse = paymentClient.findPaymentRequest(URI.create(paymentRequestUrl.getUrl()));

        val paymentRequest = Try(() -> PaymentRequest.parseFrom(parseResourceAsByteArray(paymentRequestResponse)))
                .getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new);
        val paymentSession = Try(() -> new PaymentSession(paymentRequest, false))
                .getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new);

        // Verify PaymentRequestPki throws an exception if the certificate cannot be trust, but as it uses the same exception type for all, it is quite difficult to know the reason...
        val pkiData = Try(() -> verifyPaymentRequestPki(paymentRequest, keyStore)).onFailure(t -> log.warn("Couldn't verify the payment request", t)).toOption();

        return new PaymentRequestDto(
                paymentSession.getExpires(), paymentSession.isExpired(), paymentSession.getDate(),
                Option(paymentSession.getMemo()).getOrElse("No Memo"),
                parseMerchantDataAsBytes(paymentSession.getMerchantData()).getOrElse("No Merchant Data"),
                paymentSession.getPaymentUrl(), paymentSession.getValue().value,
                paymentSession.getSendRequest().tx.getOutputs().stream().map(transactionOutput -> Pair.of(transactionOutput.getValue(), new Script(transactionOutput.getScriptBytes()).getToAddress(TestNet3Params.get())))
                        .map(coinAddressPair -> new PaymentRequestDto.AddressAndAmount(coinAddressPair.getRight().toBase58(), coinAddressPair.getLeft().value)).collect(toList()),
                pkiData.map(pkiVerificationData -> pkiVerificationData.displayName).getOrElse("No Display Name"),
                pkiData.map(pkiVerificationData -> pkiVerificationData.rootAuthorityName).getOrElse("No Root Authority Name")
        );
    }

    PaymentAckDto sendPayment(UUID walletId, @RequestBody BroadcastPaymentDto broadcastPaymentDto) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        List<Transaction> transactions = broadcastPaymentDto.getPayment().getTransactions()
                .map(transactionDto -> {
                    val transaction = new Transaction(networkParameters());
                    transactionDto.getOutputs().forEach(transactionOutputDto -> transaction.addOutput(new TransactionOutput(networkParameters(), null, Coin.valueOf(transactionOutputDto.getAmount()), fromBase58(TestNet3Params.get(), transactionOutputDto.getAddress()))));

                    return transaction;
                })
                .map(SendRequest::forTx)
                .map(sendRequest -> Try(() -> wallet.sendCoins(sendRequest)))
                .peek(tryTransaction -> tryTransaction.onFailure(t -> log.error("Couldn't send coins for walletId={}", walletId, t)))
                .peek(tryTransaction -> tryTransaction.onSuccess(sendResult -> sendResult.broadcastComplete.addListener(() -> log.info("Transaction of wallet={} broadcast successfully", walletId), newSingleThreadExecutor())))
                .map(Try::toOption)
                .flatMap(Option::toList)
                .map(sendResult -> sendResult.tx);

        val payment = createPaymentMessage(
                transactions.toJavaList(),
                broadcastPaymentDto.getPayment().getRefundOutputs().map(
                        paymentRefundOutputDto -> createPayToAddressOutput(Coin.valueOf(paymentRefundOutputDto.getAmount()), fromBase58(networkParameters(), paymentRefundOutputDto.getAddress()))
                ).toJavaList(),
                broadcastPaymentDto.getPayment().getMemo(),
                Option(broadcastPaymentDto.getPayment().getMerchant()).map(ByteString::copyFromUtf8).map(ByteString::toByteArray).getOrNull()
        );

        val paymentResponse = paymentClient.broadcastPayment(URI.create(broadcastPaymentDto.getPaymentUrl()), payment.toByteArray());

        val paymentAck = Try(() -> PaymentACK.parseFrom(parseResourceAsByteArray(paymentResponse)))
                .getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new);
        val responsePayment = paymentAck.getPayment();
        val responseTransactions = List.ofAll(responsePayment.getTransactionsList())
                .map(transactionAsByteString -> Try(() -> new Transaction(networkParameters(), transactionAsByteString.toByteArray())).onFailure(Throwable::printStackTrace).toOption())
                .flatMap(Option::toList);

        return new PaymentAckDto(
                new PaymentDto(
                        responsePayment.getMemo(),
                        Option(responsePayment.getMerchantData()).map(ByteString::toStringUtf8).getOrElse("No Merchant Data"),
                        List.ofAll(responsePayment.getRefundToList()).map(output -> new PaymentDto.PaymentRefundOutputDto(
                                new Script(output.getScript().toByteArray()).getToAddress(networkParameters()).toBase58(),
                                output.getAmount()
                        )),
                        responseTransactions.map(responseTransaction -> new TransactionDto(
                                responseTransaction.getHashAsString(),
                                responseTransaction.getLockTime(),
                                List.ofAll(responseTransaction.getInputs()).map(transactionInput -> new TransactionInputDto(String.valueOf(transactionInput.getOutpoint().getHash()), (int) transactionInput.getOutpoint().getIndex(), HEX.encode(transactionInput.getScriptSig().getProgram()))),
                                List.ofAll(responseTransaction.getOutputs()).map(transactionOutput -> new TransactionOutputDto(new Script(transactionOutput.getScriptBytes()).getToAddress(TestNet3Params.get()).toBase58(), transactionOutput.getValue().value)),
                                (int) responseTransaction.getVersion(),
                                Option(responseTransaction.getFee()).map(Coin::longValue).getOrNull()
                        ))
                ),
                Option(paymentAck).map(PaymentACK::getMemo).getOrElse("No ACK Memo"));
    }

    private Option<String> parseMerchantDataAsBytes(byte[] merchantDataAsBytes) {
        return Option(merchantDataAsBytes).flatMap(merchantData -> Try(() -> ByteString.copyFrom(merchantData).toStringUtf8()).onFailure(t -> log.warn("Couldn't decode merchant data", t)).toOption());
    }

    private byte[] parseResourceAsByteArray(Resource resource) throws IOException {

        val resourceAsBytes = new byte[resource.getInputStream().available()];
        resource.getInputStream().read(resourceAsBytes);

        return resourceAsBytes;
    }


}
