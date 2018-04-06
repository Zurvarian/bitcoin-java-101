package bitcoin.transaction.dto;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class CreateTransactionDto {

    String address;

    BigDecimal amount;
}
