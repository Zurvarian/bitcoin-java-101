package bitcoin.transaction.dto;

import io.vavr.collection.List;
import lombok.Value;

@Value
public class TransactionDto {

    String txHash;

    Long lockTime;

    List<TransactionInputDto> inputs;

    List<TransactionOutputDto> outputs;

    Integer version;

    Long fee;

    @Value
    public static class TransactionInputDto {

        String txHash;

        int vout;

        String scriptSig;
    }

    @Value
    public static class TransactionOutputDto {

        String address;

        Long amount;
    }
}
