package bitcoin.wallet.dto;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class BalanceDto {

    BigDecimal balance;

}
