package com.banka1.banking.saga;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class OTCTransaction {
    private final Long sellerAccountId;
    private final Long buyerAccountId;
    private final Double amount;
    private OTCTransactionStage stage = OTCTransactionStage.INITIALIZED;

    public void nextStage() {
        setStage(OTCTransactionStage.values()[getStage().ordinal() + 1]);
    }
}
