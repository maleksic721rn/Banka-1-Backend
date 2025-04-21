package com.banka1.banking.dto.interbank.newtx;

import lombok.Data;

@Data
public class TxAccountDTO {
    private String type; // "PERSON" ili "ACCOUNT"
    private ForeignBankIdDTO id;       // ako je PERSON
    private String num;                // ako je ACCOUNT
}
