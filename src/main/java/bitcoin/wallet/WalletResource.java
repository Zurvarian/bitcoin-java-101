package bitcoin.wallet;

import bitcoin.wallet.dto.AddressDto;
import bitcoin.wallet.dto.BalanceDto;
import bitcoin.wallet.dto.CreateWalletDto;
import bitcoin.wallet.dto.MnemonicsDto;
import bitcoin.wallet.dto.WalletDto;
import io.vavr.collection.List;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.bitcoinj.wallet.Wallet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.UUID;

import static io.vavr.API.Option;
import static lombok.AccessLevel.PRIVATE;
import static org.bitcoinj.wallet.Wallet.BalanceType.AVAILABLE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@AllArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@RequestMapping(path = "/wallet", produces = APPLICATION_JSON_VALUE)
public class WalletResource {

    WalletService walletService;

    @GetMapping(path = "/mnemonic")
    public MnemonicsDto generateWalletMnemonics(@RequestParam(value = "wordNumber", required = false) Integer wordNumber) {
        return new MnemonicsDto(walletService.mnemonics(Option(wordNumber).getOrElse(12)).mkString(" "));
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    public WalletDto createWallet(@Valid @RequestBody CreateWalletDto createWalletDto) {
        return new WalletDto(walletService.createWalletWithMnemonics(createWalletDto.getPassword(), List.of(createWalletDto.getMnemonics().split(" ")))._1);
    }

    @GetMapping(path = "/{walletId}")
    public WalletDto findWalletById(@PathVariable("walletId") UUID walletId) {
        return new WalletDto(walletService.findWalletById(walletId)._1);
    }

    @PostMapping(path = "/unlock/{walletId}")
    public WalletDto unlockWallet(@PathVariable("walletId") UUID walletId) {
        return new WalletDto(walletService.unlockWallet(walletId)._1);
    }

    @GetMapping(path = "/{walletId}/balance")
    public BalanceDto findBalanceOfWalletById(@PathVariable("walletId") UUID walletId, @RequestParam(value = "balanceType", required = false) Wallet.BalanceType balanceType) {
        return new BalanceDto(walletService.findBalanceOfWalletById(walletId, Option(balanceType).getOrElse(AVAILABLE)));
    }

    @PutMapping(path = "/{walletId}/address")
    public AddressDto deriveReceiveAddress(@PathVariable("walletId") UUID walletId) {
        return new AddressDto(walletService.deriveReceiveAddress(walletId).toBase58());
    }
}
