package com.banka1.banking.dto.interbank.newtx;

import lombok.Data;

@Data
public class VerificationTokenDTO {
    private int routingNumber;
    private String token;
}