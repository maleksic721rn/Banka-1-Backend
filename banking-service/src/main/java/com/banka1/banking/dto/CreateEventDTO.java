package com.banka1.banking.dto;

import com.banka1.banking.dto.interbank.InterbankMessageType;
import com.banka1.banking.models.helper.IdempotenceKey;
import com.banka1.banking.models.interbank.EventDirection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class CreateEventDTO {

    private InterbankMessageType messageType;

    private String payload;

    private String url;
}
