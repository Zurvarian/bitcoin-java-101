package bitcoin.payment.dto;

import lombok.Value;

import java.util.Collection;
import java.util.Date;

@Value
public class PaymentRequestDto {

    Date expiresAt;

    Boolean expired;

    Date creationDate;

    String memo;

    String merchant;

    String paymentUrl;

    Long totalAmount;

    Collection<AddressAndAmount> outputs;

    String pkiDisplayName;

    String pkiRootAuthorityName;

    @Value
    public static class AddressAndAmount {

        String address;

        Long amount;

    }
}
