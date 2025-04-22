package com.banka1.banking.dto.interbank.newtx;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PostingDTO {
    private TxAccountDTO account;
    private double amount;
    private AssetDTO asset;
}
