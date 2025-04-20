package com.banka1.banking.dto.interbank.newtx;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TxAccountDTO {
    private String type; // "PERSON" ili "ACCOUNT"
    private ForeignBankIdDTO id;       // ako je PERSON
    private String num;                // ako je ACCOUNT
}
