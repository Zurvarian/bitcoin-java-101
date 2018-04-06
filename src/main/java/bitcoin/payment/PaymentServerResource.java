package bitcoin.payment;

import io.swagger.annotations.Api;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.UUID;

import static io.vavr.API.Option;
import static lombok.AccessLevel.PRIVATE;

@Api
@RestController
@RequestMapping(path = "/payment-server")
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@Slf4j
public class PaymentServerResource {

    private static final BigDecimal HALF_BITCOIN = BigDecimal.valueOf(0.5D);

    PaymentServerService paymentServerService;

    @GetMapping(path = "/{walletId}", produces = "application/bitcoin-paymentrequest")
    public InputStreamResource createPaymentRequest(@PathVariable("walletId") UUID walletId, @RequestParam(name = "amount", required = false) BigDecimal amount) {

        log.info("Received Create Payment Request for walletId={}", walletId);

        return new InputStreamResource(new ByteArrayInputStream(paymentServerService.createPaymentRequest(walletId, Option(amount).getOrElse(HALF_BITCOIN)).toByteArray()));
    }

    @PostMapping(path = "/{walletId}", produces = "application/bitcoin-paymentack", consumes = "application/bitcoin-payment")
    public InputStreamResource callback(@RequestBody byte[] requestEntity, @PathVariable("walletId") String walletId) {

        log.info("Received Callback Payment for walletId={}", walletId);

        return new InputStreamResource(new ByteArrayInputStream(paymentServerService.callback(requestEntity, walletId).toByteArray()));
    }

}
