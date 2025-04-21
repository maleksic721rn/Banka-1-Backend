package com.banka1.banking.services;

import com.banka1.banking.dto.CreateEventDTO;
import com.banka1.banking.dto.interbank.InterbankMessageDTO;
import com.banka1.banking.dto.interbank.InterbankMessageType;
import com.banka1.banking.dto.interbank.VoteDTO;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class InterbankService implements InterbankOperationService {

    private final EventService eventService;
    private final EventExecutorService eventExecutorService;
    private final ObjectMapper objectMapper;
    private final TransferService transferService;

    public InterbankService(EventService eventService, EventExecutorService eventExecutorService, ObjectMapper objectMapper, @Lazy TransferService transferService) {
        this.eventService = eventService;
        this.eventExecutorService = eventExecutorService;
        this.objectMapper = objectMapper;
        this.transferService = transferService;
    }

    public void sendInterbankMessage(InterbankMessageDTO<?> messageDto, String targetUrl) {
        Event event;
        try {

            validateMessageByType(messageDto);

            String payloadJson = objectMapper.writeValueAsString(messageDto);
            System.out.println("trying to send interbank message: " + payloadJson);
            event = eventService.createEvent(new CreateEventDTO(
                    messageDto,
                    payloadJson,
                    targetUrl
            ));

            System.out.println("Attempting to send event: " + event.getId());

        } catch (Exception ex) {
            throw new RuntimeException("Failed to send interbank message", ex);
        }

        try {
            eventExecutorService.attemptEventAsync(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send interbank message", e);
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
                                new CurrencyAsset(transfer.getToCurrency().getCode().toString())
                        )
                ),
                new PostingDTO(
                        new TxAccountDTO("PERSON", new ForeignBankIdDTO(
                                444,
                                transfer.getNote()
                        ), ""),
                        -transfer.getAmount(),
                        new MonetaryAssetDTO(
                                new CurrencyAsset(transfer.getFromCurrency().getCode().toString())
                        )
                )
        ));

        message.setMessage(transfer.getPaymentDescription());

        transaction.setMessage(message);


        // Send the message
        // execute after five seconds for testing purposes

        System.out.println("Sending interbank message: " + transaction);

        try {
            Thread.sleep(1000);
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

    public VoteDTO webhook(InterbankMessageDTO<?> messageDto, String rawPayload, String sourceUrl) {
//        eventService.receiveEvent(messageDto, rawPayload, sourceUrl);

        VoteDTO response = new VoteDTO();

        switch (messageDto.getMessageType()) {
            case NEW_TX :
                System.out.println("Received NEW_TX message: " + messageDto.getMessage());
                response.setVote("YES");
                response.setReasons(List.of());
            case COMMIT_TX :
                // TODO
                break;
            case ROLLBACK_TX :
                // TODO
                break;
            default:
                throw new IllegalArgumentException("Unknown message type");

        }

        return response;
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

    @Override
    public void sendCommit(Event event) {
        System.out.println("Sending commit for event: " + event.getId());

        InterbankMessageDTO<CommitTransactionDTO> message = new InterbankMessageDTO<>();
        message.setMessageType(InterbankMessageType.COMMIT_TX);
        IdempotenceKey idempotenceKey = generateIdempotenceKey(message);
        message.setIdempotenceKey(idempotenceKey);

        CommitTransactionDTO commitTransactionDTO = new CommitTransactionDTO();
        commitTransactionDTO.setTransactionId(event.getIdempotenceKey());

        message.setMessage(commitTransactionDTO);



        try {
            Thread.sleep(1000);
            sendInterbankMessage(message, "http://localhost:8082/interbank");

            transferService.commitForeignBankTransfer(event.getIdempotenceKey());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendRollback(Event event) {
        System.out.println("Sending rollback for event: " + event.getId());

        InterbankMessageDTO<RollbackTransactionDTO> message = new InterbankMessageDTO<>();
        message.setMessageType(InterbankMessageType.ROLLBACK_TX);
        IdempotenceKey idempotenceKey = generateIdempotenceKey(message);
        message.setIdempotenceKey(idempotenceKey);

        RollbackTransactionDTO rollbackTransactionDTO = new RollbackTransactionDTO();
        rollbackTransactionDTO.setTransactionId(event.getIdempotenceKey());

        message.setMessage(rollbackTransactionDTO);

        try {
            Thread.sleep(1000);
            sendInterbankMessage(message, "http://localhost:8082/interbank");

            transferService.rollbackForeignBankTransfer(event.getIdempotenceKey());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
