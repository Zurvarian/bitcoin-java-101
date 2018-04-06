package bitcoin.wallet.dto;

import io.vavr.collection.List;
import lombok.Value;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigInteger;
import java.util.UUID;

@Value
public class WalletDto {

    @NotNull
    UUID walletId;

}
