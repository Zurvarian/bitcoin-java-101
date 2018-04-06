package bitcoin.payment;

import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.crypto.TrustStoreLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyStore;

import static io.vavr.API.Try;

@Configuration
@Slf4j
public class PaymentConfig {

    @Bean
    KeyStore keyStore() {
        return Try(() -> new TrustStoreLoader.DefaultTrustStoreLoader().getKeyStore()).onFailure(t -> log.error("Couldn't load the keystore", t)).getOrElse((KeyStore) null);
    }
}
