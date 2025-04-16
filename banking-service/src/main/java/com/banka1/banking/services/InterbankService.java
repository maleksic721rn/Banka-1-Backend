package com.banka1.banking.services;

import com.banka1.banking.dto.CreateEventDTO;
import com.banka1.banking.dto.interbank.InterbankMessageDTO;
import com.banka1.banking.dto.interbank.InterbankMessageType;
import com.banka1.banking.dto.interbank.committx.CommitTransactionDTO;
import com.banka1.banking.dto.interbank.newtx.InterbankTransactionDTO;
import com.banka1.banking.dto.interbank.rollbacktx.RollbackTransactionDTO;
import com.banka1.banking.models.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterbankService {

    private final EventService eventService;
    private final EventExecutorService eventExecutorService;
    private final ObjectMapper objectMapper;

    public void sendInterbankMessage(InterbankMessageDTO<?> messageDto, String targetUrl) {
        try {

            validateMessageByType(messageDto);

            String payloadJson = objectMapper.writeValueAsString(messageDto.getMessage());

            Event event = eventService.createEvent(new CreateEventDTO(
                    messageDto.getMessageType(),
                    payloadJson,
                    targetUrl
            ));

            eventExecutorService.attemptEventAsync(event);

        } catch (Exception ex) {
            throw new RuntimeException("Failed to send interbank message", ex);
        }
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
