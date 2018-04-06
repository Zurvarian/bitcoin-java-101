package bitcoin.wallet;

import io.vavr.control.Option;
import lombok.experimental.FieldDefaults;
import org.bitcoinj.wallet.Wallet;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static lombok.AccessLevel.PRIVATE;

@Component
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class WalletRepository {

    Map<UUID, Wallet> walletById = new ConcurrentHashMap<>();

    public Option<Wallet> findWalletById(UUID walletId) {
        return Option.of(walletById.get(walletId));
    }

    void save(UUID walletId, Wallet wallet) {
        walletById.put(walletId, wallet);
    }
}
