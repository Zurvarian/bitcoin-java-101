package bitcoin.transaction;

import bitcoin.wallet.WalletRepository;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.SendRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import static bitcoin.config.NetworkConfig.networkParameters;
import static java.math.MathContext.DECIMAL128;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static lombok.AccessLevel.PRIVATE;

@Component
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@Slf4j
public class TransactionService {

    private static final BigDecimal SATOSHI_TO_BITCOIN_CONVERSION = BigDecimal.valueOf(100000000L);

    WalletRepository walletRepository;

    Set<Transaction> findTransactionsOfWallet(UUID walletId) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        return HashSet.ofAll(wallet.getTransactions(false));
    }

    Transaction findTransactionOfWalletByHash(UUID walletId, Sha256Hash txHash) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        return wallet.getTransaction(txHash);
    }

    Set<Transaction> findPendingTransactionsOfWallet(UUID walletId) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        return HashSet.ofAll(wallet.getPendingTransactions());
    }

    Set<Transaction> findRecentTransactionsOfWallet(UUID walletId, int numberOfTransactions) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        return HashSet.ofAll(wallet.getRecentTransactions(numberOfTransactions, false));
    }

    Set<Transaction> findTransactionsOfWalletUsingAddress(UUID walletId, Address address) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        return HashSet.ofAll(wallet.getTransactions(false)).filter(transactionContainsAddress(address));
    }

    Transaction sendFundsToAddress(UUID walletId, Address address, BigDecimal amount) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        val sendResponse = Try.of(() -> wallet.sendCoins(SendRequest.to(address, Coin.valueOf(amount.multiply(SATOSHI_TO_BITCOIN_CONVERSION, DECIMAL128).longValueExact()))))
                .getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new);

        sendResponse.broadcastComplete.addListener(() -> log.info("Transaction of wallet={} for address={} and amount={} broadcast successfully", walletId, address, amount), newSingleThreadExecutor());

        return sendResponse.tx;
    }

    Transaction createTransaction(UUID walletId, Address address, BigDecimal amount) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        return Try.of(() -> wallet.createSend(address, Coin.valueOf(amount.multiply(SATOSHI_TO_BITCOIN_CONVERSION, DECIMAL128).longValueExact())))
                .getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new);
    }

    Transaction broadcastTransaction(UUID walletId, Transaction transaction) {

        val wallet = walletRepository.findWalletById(walletId).getOrElseThrow(() -> new RuntimeException(String.format("Wallet not found with walletId=%s", walletId)));

        val sendResponse = Try.of(() -> wallet.sendCoins(SendRequest.forTx(transaction))).getOrElseThrow((Function<? super Throwable, RuntimeException>) RuntimeException::new);

        sendResponse.broadcastComplete.addListener(() -> log.info("Transaction of wallet={} for transaction={} broadcast successfully", walletId, transaction), newSingleThreadExecutor());

        return sendResponse.tx;
    }

    private Predicate<Transaction> transactionContainsAddress(Address address) {
        return transaction -> List.ofAll(transaction.getInputs()).map(TransactionInput::getConnectedOutput).flatMap(Option::of).map(TransactionOutput::getScriptPubKey).map(scriptPubKey -> scriptPubKey.getToAddress(networkParameters())).exists(address::equals)
                || List.ofAll(transaction.getOutputs()).map(TransactionOutput::getScriptPubKey).map(scriptPubKey -> scriptPubKey.getToAddress(networkParameters())).exists(address::equals);

    }


}
