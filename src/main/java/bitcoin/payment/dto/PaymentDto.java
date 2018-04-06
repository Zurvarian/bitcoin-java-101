package bitcoin.payment.dto;

import bitcoin.transaction.dto.TransactionDto;
import io.vavr.collection.List;
import lombok.Value;

@Value
public class PaymentDto {

    String memo;

    String merchant;

    List<PaymentRefundOutputDto> refundOutputs;

    List<TransactionDto> transactions;

    @Value
    public static class PaymentRefundOutputDto {

        String address;

        Long amount;

    }

}
