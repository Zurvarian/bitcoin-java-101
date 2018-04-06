package bitcoin.transaction.dto;

import io.vavr.collection.Set;
import lombok.Value;

@Value
public class TransactionsDto {

    Set<TransactionDto> transactions;
}
