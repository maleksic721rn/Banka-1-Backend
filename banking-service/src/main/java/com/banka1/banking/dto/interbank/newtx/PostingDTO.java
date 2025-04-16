package com.banka1.banking.dto.interbank.newtx;

import lombok.Data;

@Data
public class PostingDTO {
    private TxAccountDTO account;
    private double amount;
    private AssetDTO asset;
}
