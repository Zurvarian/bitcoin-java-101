package bitcoin.payment;

import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoin.protocols.payments.Protos.PaymentACK;
import org.bitcoin.protocols.payments.Protos.PaymentRequest;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.crypto.TrustStoreLoader.DefaultTrustStoreLoader;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.security.KeyStore;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.bitcoinj.core.Address.fromBase58;
import static org.bitcoinj.protocols.payments.PaymentProtocol.MIMETYPE_PAYMENT;
import static org.bitcoinj.protocols.payments.PaymentProtocol.MIMETYPE_PAYMENTACK;
import static org.bitcoinj.protocols.payments.PaymentProtocol.MIMETYPE_PAYMENTREQUEST;
import static org.bitcoinj.protocols.payments.PaymentProtocol.createPaymentMessage;
import static org.bitcoinj.protocols.payments.PaymentProtocol.parsePaymentAck;
import static org.bitcoinj.protocols.payments.PaymentProtocol.verifyPaymentRequestPki;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

public class PaymentTest {

    private KeyStore keyStore;

    private HttpHeaders paymentRequestHeaders;

    private HttpHeaders paymentHeaders;

    private Address refundAddress;

    @Before
    public void setUp() throws Exception {
        keyStore = new DefaultTrustStoreLoader().getKeyStore();

        paymentRequestHeaders = new HttpHeaders();
        paymentRequestHeaders.add(ACCEPT, MIMETYPE_PAYMENTREQUEST);

        paymentHeaders = new HttpHeaders();
        paymentHeaders.add(CONTENT_TYPE, MIMETYPE_PAYMENT);
        paymentHeaders.add(ACCEPT, MIMETYPE_PAYMENTACK);

        refundAddress = fromBase58(TestNet3Params.get(), "mqw8Pt7N4BbjjfBW551UT5erRweQdeiJsZ");
    }

    @Ignore
    @Test
    public void testPaymentBitPay() throws IOException, PaymentProtocolException {

        Context.getOrCreate(TestNet3Params.get());

        val url = "https://test.bitpay.com/i/VbfyNbfEgXu7zFs3zyEYsU";
        val restTemplate = new RestTemplate();

        val paymentRequestResponse = restTemplate.exchange(url, GET, new HttpEntity<>(paymentRequestHeaders), Resource.class);

        System.out.println("PaymentRequest statusCode=" + paymentRequestResponse.getStatusCode());

        val paymentRequest = PaymentRequest.parseFrom(parseResourceAsByteArray(paymentRequestResponse.getBody()));
        // If verifyPki is false then we can do certificate validation later on, just that we need the payment request, so... Either the model entity contains the InputStream, either we do it at the same spot... Bitcoinj sucks
        val paymentSession = new PaymentSession(paymentRequest, false);

        // Not sure how to identify if a request is self-signed or other type of not 100% valid certificates.
        // On the other hand, if the certificate is not valid at all, an exception will be thrown, so we will need to control that with Try.of() logic... Bitcoinj sucks
        val pkiData = verifyPaymentRequestPki(paymentRequest, keyStore);

        // Data can be safely extracted from PaymentSession
        System.out.println("expiresAt=" + paymentSession.getExpires());
        System.out.println("expired=" + paymentSession.isExpired());
        System.out.println("date=" + paymentSession.getDate());
        System.out.println("memo=" + paymentSession.getMemo());
        System.out.println("merchant=" + Option.of(paymentSession.getMerchantData()).flatMap(merchantData -> Try.of(() -> new String(merchantData, "UTF-8")).onFailure(System.err::println).toOption()).getOrElse("No Merchant Data"));
        System.out.println("paymentUrl=" + paymentSession.getPaymentUrl());
        System.out.println("totalAmount=" + paymentSession.getValue());
        System.out.println("outputs=" + paymentSession.getSendRequest().tx.getOutputs().stream().map(transactionOutput -> Pair.of(transactionOutput.getValue(), transactionOutput.getAddressFromP2PKHScript(paymentSession.getNetworkParameters()))).map(Pair::toString).collect(joining("; ", "Coin->", "")));
        System.out.println("pki-displayName=" + Option.of(pkiData).map(pkiVerificationData -> pkiVerificationData.displayName).getOrElse("No Display Name"));
        System.out.println("pki-rootAuthorityName=" + Option.of(pkiData).map(pkiVerificationData -> pkiVerificationData.rootAuthorityName).getOrElse("No Root Authority Name"));

        Context.getOrCreate(TestNet3Params.get());

        // New transactions should be created using the output info from the payment and with our own wallet inputs
        val transaction = new Transaction(TestNet3Params.get());
        transaction.addInput(new TransactionInput(TestNet3Params.get(), null, new byte[]{}, new TransactionOutPoint(TestNet3Params.get(), 0L, Sha256Hash.ZERO_HASH)));
        paymentSession.getSendRequest().tx.getOutputs().forEach(transaction::addOutput);

        val payment = createPaymentMessage(singletonList(transaction), paymentSession.getValue(), refundAddress, paymentSession.getMemo(), paymentSession.getMerchantData());

        val paymentResponse = restTemplate.exchange(url, POST, new HttpEntity<>(payment.toByteArray(), paymentHeaders), Resource.class);

        System.out.println("Payment statusCode=" + paymentResponse.getStatusCode());

        val paymentAck = parsePaymentAck(PaymentACK.parseFrom(parseResourceAsByteArray(paymentResponse.getBody())));

        // Payment Ack memo is different from PaymentRequest/Payment memo, don't confuse them!
        System.out.println(paymentAck.getMemo());
    }

    // Both PaymentRequest and PaymentAck parseFrom has an overload for InputStream, but then we will need to couple BitcoinJ to the Client or either we need to pass an InputStream around, neither of the two options are good to me,
    // so I'm copying the bytes into an array and use that as response. Please, consider using a copy of the array or to protect it against modifications.
    private byte[] parseResourceAsByteArray(Resource resource) throws IOException {

        val resourceAsBytes = new byte[resource.getInputStream().available()];
        val readBytesCount = resource.getInputStream().read(resourceAsBytes);

        System.out.println("Count read bytes=" + readBytesCount);

        return resourceAsBytes;
    }
}
