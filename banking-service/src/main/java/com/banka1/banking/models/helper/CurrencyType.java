package com.banka1.banking.models.helper;

public enum CurrencyType {
    RSD,
    EUR,
    USD,
    CHF,
    GBP,
    JPY,
    CAD,
    AUD;

    public static CurrencyType fromString(String code) {
        try {
            return CurrencyType.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RuntimeException("Unsupported currency: " + code);
        }
    }
}
