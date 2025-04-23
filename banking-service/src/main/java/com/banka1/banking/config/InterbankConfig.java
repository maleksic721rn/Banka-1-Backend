package com.banka1.banking.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class InterbankConfig {
    @Value("${ROUTING_NUMBER}")
    private String routingNumber;

    @Value("${FOREIGN_BANK_ROUTING_NUMBER}")
    private String foreignBankRoutingNumber;

    @Value("${INTERBANK_TARGET_URL}")
    private String interbankTargetUrl;
}
