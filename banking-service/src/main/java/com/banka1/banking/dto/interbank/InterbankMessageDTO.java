package com.banka1.banking.dto.interbank;

import com.banka1.banking.models.helper.IdempotenceKey;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterbankMessageDTO<T> {

    private IdempotenceKey idempotenceKey;
    private InterbankMessageType messageType;
    private T message;
}