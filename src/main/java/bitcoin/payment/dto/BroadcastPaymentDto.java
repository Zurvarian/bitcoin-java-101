package bitcoin.payment.dto;

import lombok.Value;

@Value
public class BroadcastPaymentDto {

    String paymentUrl;

    PaymentDto payment;

}
