package com.banka1.banking.saga;

public enum OTCTransactionStage {
    INITIALIZED,
    ASSETS_RESERVED,
    ASSETS_TRANSFERED,
    FINISHED
}
