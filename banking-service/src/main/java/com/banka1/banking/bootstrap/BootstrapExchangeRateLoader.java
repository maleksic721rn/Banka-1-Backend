package com.banka1.banking.bootstrap;

import com.banka1.banking.dto.CreateEventDTO;
import com.banka1.banking.dto.interbank.InterbankMessageDTO;
import com.banka1.banking.dto.interbank.InterbankMessageType;
import com.banka1.banking.dto.interbank.rollbacktx.RollbackTransactionDTO;
import com.banka1.banking.models.Event;
import com.banka1.banking.models.helper.IdempotenceKey;
import com.banka1.banking.services.CurrencyService;
import com.banka1.banking.services.EventExecutorService;
import com.banka1.banking.services.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapExchangeRateLoader implements CommandLineRunner {

    private final CurrencyService currencyService;
    private final EventService eventService;
    private final EventExecutorService eventExecutorService;

    public BootstrapExchangeRateLoader(CurrencyService currencyService,
                                       EventService eventService,
                                       EventExecutorService eventExecutorService) {
        this.currencyService = currencyService;
        this.eventService = eventService;
        this.eventExecutorService = eventExecutorService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Fetching exchange rates on startup ===");
        currencyService.fetchExchangeRates();
        System.out.println("=== Exchange rates fetched successfully ===");


//         test events

        System.out.println("=== Testing event creation ===");
        InterbankMessageDTO<RollbackTransactionDTO> message = new InterbankMessageDTO<>();

        message.setMessageType(InterbankMessageType.ROLLBACK_TX);
        IdempotenceKey idempotenceKey = new IdempotenceKey();
        idempotenceKey.setRoutingNumber(123);
        idempotenceKey.setLocallyGeneratedKey("123456789");
        message.setIdempotenceKey(idempotenceKey);
        message.setMessage(new RollbackTransactionDTO(
            idempotenceKey
        ));

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(message.getMessage());
        System.out.println(json);

        Event event = eventService.createEvent(new CreateEventDTO(
                InterbankMessageType.ROLLBACK_TX,
                json,
                "http://localhost:8082/interbank"
        ));

        System.out.println("=== Executing event ===");

        eventExecutorService.attemptEventAsync(event);

        System.out.println("=== Event executed successfully ===");
    }
}
