package com.banka1.banking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OTCTransactionACKDTO {
    private String uid;
    private boolean failure = false;
    private String message;
}
