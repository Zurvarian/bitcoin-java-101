package bitcoin.wallet;

import com.google.common.util.concurrent.Service;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Function;

import static bitcoin.config.NetworkConfig.networkParameters;
import static io.vavr.API.Tuple;
import static java.math.RoundingMode.HALF_EVEN;
import static java.util.UUID.nameUUIDFromBytes;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static lombok.AccessLevel.PRIVATE;

@Component
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@Slf4j
public class WalletService {

    private static final int MIN_WORDS = 3;

    private static final int MIN_BITS = 4;

    private static final Path PROJECT_BASE = Paths.get("development/wallets");

    private static final BigDecimal SATOSHI_TO_BITCOIN_CONVERSION = BigDecimal.valueOf(100000000L);

    WalletRepository walletRepository;

    List<String> mnemonics(int wordNumber) {
        return Try.of(() -> MnemonicCode.INSTANCE.toMnemonic(getEntropy(new SecureRandom(), wordNumberToNumberOfBits(wordNumber)))).map(List::ofAll)
                .getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new);
    }

    Tuple2<UUID, Wallet> createWalletWithMnemonics(String passphrase, List<String> mnemonic) {

        val walletId = nameUUIDFromBytes(mnemonic.mkString().getBytes());
        val walletAppKit = walletAppKit(walletId)
                .restoreWalletFromSeed(new DeterministicSeed(mnemonic.toJavaList(), null, passphrase, 0L));

        walletAppKit.startAsync()
                .addListener(listenerForWallet(walletId, () -> {
                    walletRepository.save(walletId, walletAppKit.wallet());
                    walletAppKit.wallet().addTransactionConfidenceEventListener(newSingleThreadExecutor(), transactionConfidenceEventListener(walletId));
                    walletAppKit.wallet().addCoinsReceivedEventListener(newSingleThreadExecutor(), walletCoinsReceivedEventListener(walletId));
                }), newSingleThreadExecutor());

        return Tuple(walletId, walletAppKit.wallet());
    }

    Tuple2<UUID, Wallet> unlockWallet(UUID walletId) {

        verifyThereIsAWalletToUnlock(walletId);

        val walletAppKit = walletAppKit(walletId);

        walletAppKit.startAsync()
                .addListener(listenerForWallet(walletId, () -> {
                    walletRepository.save(walletId, walletAppKit.wallet());
                    walletAppKit.wallet().addTransactionConfidenceEventListener(newSingleThreadExecutor(), transactionConfidenceEventListener(walletId));
                    walletAppKit.wallet().addCoinsReceivedEventListener(newSingleThreadExecutor(), walletCoinsReceivedEventListener(walletId));
                }), newSingleThreadExecutor());

        return Tuple(walletId, walletAppKit.wallet());
    }

    Tuple2<UUID, Wallet> findWalletById(UUID walletId) {
        return Tuple(walletId, walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId))));
    }

    BigDecimal findBalanceOfWalletById(UUID walletId, Wallet.BalanceType balanceType) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        return new BigDecimal(wallet.getBalance(balanceType).longValue()).divide(SATOSHI_TO_BITCOIN_CONVERSION, 8, HALF_EVEN);
    }

    Address deriveReceiveAddress(UUID walletId) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        return wallet.freshReceiveAddress();
    }

    private static int wordNumberToNumberOfBits(int wordNumber) {
        int count = 0;
        while (wordNumber >= MIN_WORDS) {
            wordNumber = wordNumber / 2;
            count++;
        }
        return count == 0 ? MIN_BITS : (int) Math.pow(2, count + 1);
    }

    private static byte[] getEntropy(SecureRandom random, int bits) {
        byte[] seed = new byte[bits];
        random.nextBytes(seed);
        return seed;
    }

    private WalletAppKit walletAppKit(UUID walletId) {

        val walletAppKit = new WalletAppKit(networkParameters(), PROJECT_BASE.toFile(), walletId.toString());
        walletAppKit.setAutoSave(true);
        walletAppKit.setAutoStop(true);
        walletAppKit.setBlockingStartup(false);

        return walletAppKit;
    }

    private void verifyThereIsAWalletToUnlock(UUID walletId) {
        if (!PROJECT_BASE.resolve(walletId.toString() + ".wallet").toFile().exists()) {
            throw new RuntimeException(String.format("Wallet cannot be unlocked because it does not exists with walletId=%s", walletId));
        }
    }

    private Service.Listener listenerForWallet(UUID walletId, Runnable runnable) {
        return new Service.Listener() {
            @Override
            public void starting() {
                log.info("Starting wallet={}", walletId);
            }

            @Override
            public void running() {
                log.info("Running wallet={}", walletId);
                runnable.run();
            }

            @Override
            public void stopping(Service.State from) {
                log.info("Stopping wallet={}", walletId);
            }

            @Override
            public void terminated(Service.State from) {
                log.info("Terminated wallet={}", walletId);
            }

            @Override
            public void failed(Service.State from, Throwable failure) {
                log.error("Failed to run wallet={}", walletId, failure);
            }
        };
    }

    private TransactionConfidenceEventListener transactionConfidenceEventListener(UUID walletId) {
        return (wallet, tx) -> {
            if (tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING && tx.getConfidence().getDepthInBlocks() < 10) {
                log.info("Transaction tx={} of wallet={} now has {} confirmations", tx.getHashAsString(), walletId, tx.getConfidence().getDepthInBlocks());
            }
        };
    }

    private WalletCoinsReceivedEventListener walletCoinsReceivedEventListener(UUID walletId) {
        return (wallet, tx, prevBalance, newBalance) -> log.info("Wallet={} has received {} from tx={}", walletId, tx.getValueSentToMe(wallet), tx.getHashAsString());
    }


}
