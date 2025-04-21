package com.banka1.banking.controllers;

import com.banka1.banking.dto.interbank.InterbankMessageDTO;
import com.banka1.banking.dto.interbank.InterbankMessageType;
import com.banka1.banking.services.InterbankService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/interbank")
@RequiredArgsConstructor
public class InterbankController {

    private final InterbankService interbankService;

    @PostMapping
    public ResponseEntity<?> receiveWebhook(HttpServletRequest request) throws IOException {
        String rawPayload = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

        ObjectMapper mapper = new ObjectMapper();
        InterbankMessageDTO<?> message = mapper.readValue(rawPayload, InterbankMessageDTO.class);

        interbankService.webhook(message, rawPayload, request.getRemoteAddr());

        return ResponseEntity.ok().build();
    }

}
