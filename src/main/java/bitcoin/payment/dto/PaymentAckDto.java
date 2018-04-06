package bitcoin.payment.dto;

import lombok.Value;

@Value
public class PaymentAckDto {

    PaymentDto payment;

    String memo;

}
