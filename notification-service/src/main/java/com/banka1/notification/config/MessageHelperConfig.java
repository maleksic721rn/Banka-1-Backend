package com.banka1.notification.config;

import com.banka1.common.listener.MessageHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageHelperConfig {
    @Bean
    public MessageHelper messageHelper(Validator validator, ObjectMapper objectMapper) {
        return new MessageHelper(validator, objectMapper);
    }
}
