package com.banka1.banking.services;

import com.banka1.banking.dto.CreateEventDTO;
import com.banka1.banking.dto.interbank.InterbankMessageDTO;
import com.banka1.banking.dto.interbank.InterbankMessageType;
import com.banka1.banking.dto.interbank.committx.CommitTransactionDTO;
import com.banka1.banking.dto.interbank.newtx.ForeignBankIdDTO;
import com.banka1.banking.dto.interbank.newtx.InterbankTransactionDTO;
import com.banka1.banking.dto.interbank.newtx.PostingDTO;
import com.banka1.banking.dto.interbank.newtx.TxAccountDTO;
import com.banka1.banking.dto.interbank.newtx.assets.CurrencyAsset;
import com.banka1.banking.dto.interbank.newtx.assets.MonetaryAssetDTO;
import com.banka1.banking.dto.interbank.rollbacktx.RollbackTransactionDTO;
import com.banka1.banking.models.Event;
import com.banka1.banking.models.Transfer;
import com.banka1.banking.models.helper.IdempotenceKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterbankService {

    private final EventService eventService;
    private final EventExecutorService eventExecutorService;
    private final ObjectMapper objectMapper;

    public void sendInterbankMessage(InterbankMessageDTO<?> messageDto, String targetUrl) {
        try {

            validateMessageByType(messageDto);

            String payloadJson = objectMapper.writeValueAsString(messageDto);
            System.out.println("trying to send interbank message: " + payloadJson);
            Event event = eventService.createEvent(new CreateEventDTO(
                    messageDto,
                    payloadJson,
                    targetUrl
            ));

            System.out.println("Attempting to send event: " + event.getId());
            eventExecutorService.attemptEventAsync(event);

        } catch (Exception ex) {
            throw new RuntimeException("Failed to send interbank message", ex);
        }
    }

    public void sendNewTXMessage(Transfer transfer) {
        InterbankMessageDTO<InterbankTransactionDTO> transaction = new InterbankMessageDTO<>();
        transaction.setMessageType(InterbankMessageType.NEW_TX);

        // Set up the idempotence key
        IdempotenceKey idempotenceKey = new IdempotenceKey(111, transfer.getId().toString());
        transaction.setIdempotenceKey(idempotenceKey);

        InterbankTransactionDTO message = new InterbankTransactionDTO();

        /*
           Set idempotence key as id for the transaction, will be used to extract
         */
        message.setTransactionId(idempotenceKey);

        // Set up timestamp
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        message.setTimestamp(
                java.time.ZonedDateTime.now().format(formatter)
        );


        // Set up the transaction details
        message.setPostings(List.of(
                new PostingDTO(
                        new TxAccountDTO("PERSON", new ForeignBankIdDTO(
                                111,
                                transfer.getFromAccountId().getOwnerID().toString()
                        ), ""),
                        transfer.getAmount(),
                        new MonetaryAssetDTO(
                                "MONAS",
                                new CurrencyAsset(transfer.getToCurrency().toString())
                        )
                ),
                new PostingDTO(
                        new TxAccountDTO("PERSON", new ForeignBankIdDTO(
                                444,
                                transfer.getNote()
                        ), ""),
                        -transfer.getAmount(),
                        new MonetaryAssetDTO(
                                "MONAS",
                                new CurrencyAsset(transfer.getFromCurrency().toString())
                        )
                )
        ));

        message.setMessage(transfer.getPaymentDescription());

        transaction.setMessage(message);


        // Send the message
        // execute after five seconds for testing purposes

        System.out.println("Sending interbank message: " + transaction);

        try {
            Thread.sleep(5000);
            sendInterbankMessage(transaction, "http://localhost:8082/interbank");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private IdempotenceKey generateIdempotenceKey(InterbankMessageDTO<?> messageDto) {
        IdempotenceKey idempotenceKey = new IdempotenceKey();
        idempotenceKey.setRoutingNumber(111);
        idempotenceKey.setLocallyGeneratedKey(UUID.randomUUID().toString());
        return idempotenceKey;
    }

    public void webhook(InterbankMessageDTO<?> messageDto, String rawPayload, String sourceUrl) {
//        eventService.receiveEvent(messageDto, rawPayload, sourceUrl);

        switch (messageDto.getMessageType()) {
            case NEW_TX :
                // TODO
                break;
            case COMMIT_TX :
                // TODO
                break;
            case ROLLBACK_TX :
                // TODO
                break;
            default:
                throw new IllegalArgumentException("Unknown message type");

        }
    }

    @SuppressWarnings("unchecked")
    public void validateMessageByType(InterbankMessageDTO<?> dto) {
        InterbankMessageType type = dto.getMessageType();
        Object message = dto.getMessage();

        switch (type) {
            case NEW_TX -> {
                if (!(message instanceof InterbankTransactionDTO)) {
                    throw new IllegalArgumentException("Expected InterbankTransactionDTO for NEW_TX");
                }
            }
            case COMMIT_TX -> {
                if (!(message instanceof CommitTransactionDTO)) {
                    throw new IllegalArgumentException("Expected CommitTransactionDTO for COMMIT_TX");
                }
            }
            case ROLLBACK_TX -> {
                if (!(message instanceof RollbackTransactionDTO)) {
                    throw new IllegalArgumentException("Expected RollbackTransactionDTO for ROLLBACK_TX");
                }
            }
            default -> throw new IllegalArgumentException("Unknown message type");
        }
    }
}
