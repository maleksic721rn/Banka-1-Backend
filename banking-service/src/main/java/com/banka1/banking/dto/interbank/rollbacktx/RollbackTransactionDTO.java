package com.banka1.banking.dto.interbank.rollbacktx;

import com.banka1.banking.models.helper.IdempotenceKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@NoArgsConstructor
@Getter
@Setter
public class RollbackTransactionDTO {
    private IdempotenceKey transactionId;

    @JsonCreator
    public RollbackTransactionDTO(@JsonProperty("transactionId") IdempotenceKey transactionId) {
        this.transactionId = transactionId;
    }
}
