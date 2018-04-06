package bitcoin.wallet.dto;

import lombok.Value;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Value
public class CreateWalletDto {

    @NotNull
    @Size(min = 1)
    String password;

    @NotNull
    @Size(min = 1)
    String mnemonics;
}
