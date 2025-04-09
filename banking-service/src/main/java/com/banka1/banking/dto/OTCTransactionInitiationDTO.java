package com.banka1.banking.dto;

import lombok.Data;

@Data
public class OTCTransactionInitiationDTO {
    private String uid;
    private Long sellerAccountId;
    private Long buyerAccountId;
    private Double amount;
}
