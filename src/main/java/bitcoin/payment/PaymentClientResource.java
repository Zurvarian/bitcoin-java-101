package bitcoin.payment;

import bitcoin.payment.dto.BroadcastPaymentDto;
import bitcoin.payment.dto.PaymentAckDto;
import bitcoin.payment.dto.PaymentRequestDto;
import bitcoin.payment.dto.PaymentRequestUrl;
import io.swagger.annotations.Api;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Api
@RestController
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@RequestMapping(path = "/payment-client", consumes = APPLICATION_JSON_UTF8_VALUE, produces = APPLICATION_JSON_UTF8_VALUE)
public class PaymentClientResource {

    PaymentClientService paymentClientService;

    @RequestMapping(method = POST, path = "/payment-request")
    public PaymentRequestDto findPaymentRequest(@RequestBody PaymentRequestUrl paymentRequestUrl) {
        return paymentClientService.findPaymentRequest(paymentRequestUrl);
    }

    @RequestMapping(method = POST, path = "/payment/{walletId}")
    public PaymentAckDto sendPayment(@PathVariable("walletId") UUID walletId, @RequestBody BroadcastPaymentDto broadcastPaymentDto) {
        return paymentClientService.sendPayment(walletId, broadcastPaymentDto);
    }

}
