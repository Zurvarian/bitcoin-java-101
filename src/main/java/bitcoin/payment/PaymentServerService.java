package bitcoin.payment;

import bitcoin.wallet.WalletRepository;
import com.google.protobuf.ByteString;
import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bitcoin.protocols.payments.Protos;
import org.bitcoin.protocols.payments.Protos.Output;
import org.bitcoin.protocols.payments.Protos.Payment;
import org.bitcoin.protocols.payments.Protos.PaymentACK;
import org.bitcoin.protocols.payments.Protos.PaymentDetails;
import org.bitcoin.protocols.payments.Protos.PaymentRequest;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.UUID;
import java.util.function.Function;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static io.vavr.API.Option;
import static io.vavr.API.Try;
import static java.math.MathContext.DECIMAL128;
import static java.time.Duration.ofHours;
import static java.time.Instant.now;
import static lombok.AccessLevel.PRIVATE;
import static org.bitcoinj.script.ScriptBuilder.createOutputScript;

@Component
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@Slf4j
@EnableConfigurationProperties(PaymentRequestSignatureProperties.class)
public class PaymentServerService {

    private static final BigDecimal SATOSHI_TO_BITCOIN_CONVERSION = BigDecimal.valueOf(100000000L);

    WalletRepository walletRepository;

    Environment environment;

    PaymentRequestSignatureProperties paymentRequestSignatureProperties;

    PaymentRequest createPaymentRequest(UUID walletId, BigDecimal amount) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));
        val address = wallet.freshReceiveAddress();

        val paymentDetails = PaymentDetails.newBuilder()
                .setTime(now().getEpochSecond())
                .setMemo(String.format("Testing PaymentRequest for walletId=%s", walletId))
                .addOutputs(Output.newBuilder()
                        .setAmount(amount.multiply(SATOSHI_TO_BITCOIN_CONVERSION, DECIMAL128).longValueExact())
                        .setScript(ByteString.copyFrom(createOutputScript(address).getProgram())).build())
                .setExpires(now().plus(ofHours(1)).getEpochSecond())
                .setPaymentUrl(environment.getRequiredProperty("server.url") + "/payment-server/" + walletId)
                .setMerchantData(copyFromUtf8(walletId.toString()))
                .setNetwork(TestNet3Params.get().getPaymentProtocolId())
                .build();

        val paymentRequest = PaymentRequest.newBuilder()
                .setSerializedPaymentDetails(paymentDetails.toByteString())
                .build();

        return paymentRequestSignatureProperties.getEnabled() ? Try(() -> sign(paymentRequest)).getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new) : paymentRequest;
    }

    PaymentACK callback(byte[] requestEntity, String walletId) {

        val payment = Try(() -> Payment.parseFrom(requestEntity)).getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new);
        val transactions = List.ofAll(payment.getTransactionsList())
                .map(transactionAsByteString -> Try(() -> new Transaction(TestNet3Params.get(), transactionAsByteString.toByteArray())).onFailure(Throwable::printStackTrace).toOption())
                .flatMap(Option::toList);

        log.info("Memo={}", payment.getMemo());
        log.info("Merchant Data={}", Option(payment.getMerchantData()).map(ByteString::toStringUtf8).getOrElse("No Merchant Data"));
        log.info("Refund Outputs={}", List.ofAll(payment.getRefundToList()).zipWithIndex((output, index) -> String.format("(index=%s, address=%s, amount=%s)", index, new Script(output.getScript().toByteArray()).getToAddress(TestNet3Params.get()).toBase58(), output.getAmount())).mkCharSeq(", "));
        log.info("Transactions={}", transactions);

        return PaymentACK.newBuilder()
                .setPayment(payment)
                .setMemo(String.format("Testing PaymentAck for walletId=%s", walletId))
                .build();
    }

    private PaymentRequest sign(PaymentRequest paymentRequest) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException, InvalidKeyException, SignatureException {
        val keyStore = KeyStore.getInstance(paymentRequestSignatureProperties.getKeyStoreType());
        keyStore.load(this.getClass().getClassLoader().getResourceAsStream(paymentRequestSignatureProperties.getCertificateFileName()), paymentRequestSignatureProperties.getCertificatePassword().toCharArray());

        val certChain = keyStore.getCertificateChain(paymentRequestSignatureProperties.getCertificateChainAlias());
        val privateKey = (PrivateKey) keyStore.getKey(paymentRequestSignatureProperties.getCertificateAlias(), paymentRequestSignatureProperties.getCertificatePassword().toCharArray());

        val certChainBuilder = Protos.X509Certificates.newBuilder();
        for (Certificate certificate : certChain) {
            certChainBuilder.addCertificate(ByteString.copyFrom(certificate.getEncoded()));
        }

        val tmpRequest = PaymentRequest.newBuilder(paymentRequest)
                .setPkiType("x509+sha256")
                .setSignature(ByteString.copyFromUtf8(""))
                .setPkiData(certChainBuilder.build().toByteString())
                .build();

        val sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(tmpRequest.toByteArray());
        val signature = sig.sign();

        return PaymentRequest.newBuilder(tmpRequest)
                .setSignature(ByteString.copyFrom(signature))
                .build();
    }
}
