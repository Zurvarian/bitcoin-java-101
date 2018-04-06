package bitcoin.payment;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;

import static org.bitcoinj.protocols.payments.PaymentProtocol.MIMETYPE_PAYMENT;
import static org.bitcoinj.protocols.payments.PaymentProtocol.MIMETYPE_PAYMENTACK;
import static org.bitcoinj.protocols.payments.PaymentProtocol.MIMETYPE_PAYMENTREQUEST;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@FeignClient(name = "payment-client", url = "http://localhost:8080")
interface PaymentClient {

    @RequestMapping(method = GET, produces = MIMETYPE_PAYMENTREQUEST)
    Resource findPaymentRequest(URI endpoint);

    @RequestMapping(method = POST, produces = MIMETYPE_PAYMENTACK, consumes = MIMETYPE_PAYMENT)
    Resource broadcastPayment(URI endpoint, byte[] payment);

}
