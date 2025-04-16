package com.banka1.banking.dto.interbank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InterbankMessageType {
    NEW_TX("NEW_TX"),
    COMMIT_TX("COMMIT_TX"),
    ROLLBACK_TX("ROLLBACK_TX");

    private final String value;

    @JsonCreator
    InterbankMessageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}