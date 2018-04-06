package bitcoin.payment;

import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(level = PRIVATE)
@ConfigurationProperties(prefix = "payment-request.signature")
public class PaymentRequestSignatureProperties {

    Boolean enabled = false;

    String keyStoreType;

    String certificateFileName;

    String certificateChainAlias;

    String certificateAlias;

    String certificatePassword;

}
