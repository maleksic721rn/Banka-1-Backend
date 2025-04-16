package com.banka1.banking.dto.interbank.newtx;

import lombok.Data;

@Data
public class ForeignBankIdDTO {
    private int routingNumber;
    private String userId;
}
