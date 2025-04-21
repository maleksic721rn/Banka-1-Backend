package com.banka1.banking.dto.interbank.committx;

import com.banka1.banking.models.helper.IdempotenceKey;
import lombok.Data;

@Data
public class CommitTransactionDTO {
    private IdempotenceKey transactionId;
}
