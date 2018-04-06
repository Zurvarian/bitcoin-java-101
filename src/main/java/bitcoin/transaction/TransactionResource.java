package bitcoin.transaction;

import bitcoin.transaction.dto.CreateTransactionDto;
import bitcoin.transaction.dto.TransactionDto;
import bitcoin.transaction.dto.TransactionDto.TransactionInputDto;
import bitcoin.transaction.dto.TransactionDto.TransactionOutputDto;
import bitcoin.transaction.dto.TransactionsDto;
import io.vavr.collection.List;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static bitcoin.config.NetworkConfig.networkParameters;
import static io.vavr.API.Option;
import static lombok.AccessLevel.PRIVATE;
import static org.bitcoinj.core.Address.fromBase58;
import static org.bitcoinj.core.Utils.HEX;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@RequestMapping(path = "/wallet/{walletId}/transaction", produces = APPLICATION_JSON_VALUE)
public class TransactionResource {

    TransactionService transactionService;

    @GetMapping
    public TransactionsDto findTransactionsOfWallet(@PathVariable("walletId") UUID walletId) {
        return new TransactionsDto(transactionService.findTransactionsOfWallet(walletId).map(this::transactionToDto));
    }

    @GetMapping(path = "/recent")
    public TransactionsDto findRecentTransactionsOfWallet(@PathVariable("walletId") UUID walletId, @RequestParam(name = "numberOfTransactions", required = false) Integer numberOfTransactions) {
        return new TransactionsDto(transactionService.findRecentTransactionsOfWallet(walletId, Option(numberOfTransactions).getOrElse(10)).map(this::transactionToDto));
    }

    @GetMapping(path = "/pending")
    public TransactionsDto findPendingTransactionsOfWallet(@PathVariable("walletId") UUID walletId) {
        return new TransactionsDto(transactionService.findPendingTransactionsOfWallet(walletId).map(this::transactionToDto));
    }

    @GetMapping(path = "/address/{address}")
    public TransactionsDto findTransactionsOfWalletUsingAddress(@PathVariable("walletId") UUID walletId, @PathVariable("address") String address) {
        return new TransactionsDto(transactionService.findTransactionsOfWalletUsingAddress(walletId, Address.fromBase58(networkParameters(), address)).map(this::transactionToDto));
    }

    @GetMapping(path = "/{transactionId}")
    public TransactionDto findTransactionOfWalletByHash(@PathVariable("walletId") UUID walletId, @PathVariable("transactionId") String txHash) {
        return transactionToDto(transactionService.findTransactionOfWalletByHash(walletId, Sha256Hash.wrap(txHash)));
    }

    @PutMapping(path = "/send")
    public TransactionDto sendFundsToAddress(@PathVariable("walletId") UUID walletId, @RequestBody CreateTransactionDto createTransactionDto) {
        return transactionToDto(transactionService.sendFundsToAddress(walletId, Address.fromBase58(networkParameters(), createTransactionDto.getAddress()), createTransactionDto.getAmount()));
    }

    @PutMapping(path = "/create")
    public TransactionDto createTransaction(@PathVariable("walletId") UUID walletId, @RequestBody CreateTransactionDto createTransactionDto) {
        return transactionToDto(transactionService.createTransaction(walletId, Address.fromBase58(networkParameters(), createTransactionDto.getAddress()), createTransactionDto.getAmount()));
    }

    @PutMapping(path = "/broadcast")
    public TransactionDto broadcastTransaction(@PathVariable("walletId") UUID walletId, @RequestBody TransactionDto transactionDto) {
        return transactionToDto(transactionService.broadcastTransaction(walletId, transactionDtoToModel(transactionDto)));
    }

    private TransactionDto transactionToDto(Transaction transaction) {
        return new TransactionDto(
                transaction.getHashAsString(),
                transaction.getLockTime(),
                List.ofAll(transaction.getInputs()).map(transactionInput -> new TransactionInputDto(String.valueOf(transactionInput.getOutpoint().getHash()), (int) transactionInput.getOutpoint().getIndex(), HEX.encode(transactionInput.getScriptSig().getProgram()))),
                List.ofAll(transaction.getOutputs()).map(transactionOutput -> new TransactionOutputDto(new Script(transactionOutput.getScriptBytes()).getToAddress(networkParameters()).toBase58(), transactionOutput.getValue().value)),
                (int) transaction.getVersion(),
                Option(transaction.getFee()).map(Coin::longValue).getOrNull()
        );
    }

    private Transaction transactionDtoToModel(TransactionDto transactionDto) {
        val transaction = new Transaction(networkParameters());
        transactionDto.getInputs().forEach(transactionInputDto -> transaction.addInput(new TransactionInput(networkParameters(), null, Option(transactionInputDto.getScriptSig()).map(HEX::decode).getOrElse(() -> new byte[]{}), new TransactionOutPoint(networkParameters(), transactionInputDto.getVout(), Sha256Hash.wrap(transactionInputDto.getTxHash())))));
        transactionDto.getOutputs().forEach(transactionOutputDto -> transaction.addOutput(new TransactionOutput(networkParameters(), null, Coin.valueOf(transactionOutputDto.getAmount()), fromBase58(networkParameters(), transactionOutputDto.getAddress()))));

        return transaction;
    }
}
