package com.banka1.banking.dto;

import com.banka1.banking.models.Account;
import com.banka1.banking.models.Currency;
import com.banka1.banking.models.Transfer;
import lombok.Data;

@Data
public class TransactionResponseDTO {

    private Long id;

    private Account fromAccountId;

    private Account toAccountId;

    private Double amount;

    private Double finalAmount;

    private Double fee;

    private Boolean bankOnly = false;

    private Currency currency;

    private Long timestamp;

    private String description;

    private Transfer transfer;

    private String senderName; //osoba koja salje pare
    private String receiverName; //osoba koja ce dobiti pare
}
