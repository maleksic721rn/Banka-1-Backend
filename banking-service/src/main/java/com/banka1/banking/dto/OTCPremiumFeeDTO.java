package com.banka1.banking.dto;

import lombok.Data;

@Data
public class OTCPremiumFeeDTO {
    private Long sellerAccountId;
    private Long buyerAccountId;
    private Double amount;
}
