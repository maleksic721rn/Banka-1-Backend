package com.banka1.banking.services;

import com.banka1.banking.dto.CreateEventDTO;
import com.banka1.banking.dto.interbank.InterbankMessageDTO;
import com.banka1.banking.dto.interbank.InterbankMessageType;
import com.banka1.banking.dto.interbank.VoteDTO;
import com.banka1.banking.dto.interbank.VoteReasonDTO;
import com.banka1.banking.dto.interbank.committx.CommitTransactionDTO;
import com.banka1.banking.dto.interbank.newtx.ForeignBankIdDTO;
import com.banka1.banking.dto.interbank.newtx.InterbankTransactionDTO;
import com.banka1.banking.dto.interbank.newtx.PostingDTO;
import com.banka1.banking.dto.interbank.newtx.TxAccountDTO;
import com.banka1.banking.dto.interbank.newtx.assets.CurrencyAsset;
import com.banka1.banking.dto.interbank.newtx.assets.MonetaryAssetDTO;
import com.banka1.banking.dto.interbank.rollbacktx.RollbackTransactionDTO;
import com.banka1.banking.models.Account;
import com.banka1.banking.models.Currency;
import com.banka1.banking.models.Event;
import com.banka1.banking.models.Transfer;
import com.banka1.banking.models.helper.CurrencyType;
import com.banka1.banking.models.helper.IdempotenceKey;
import com.banka1.banking.repository.AccountRepository;
import com.banka1.banking.repository.CurrencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InterbankService implements InterbankOperationService {

    private final EventService eventService;
    private final EventExecutorService eventExecutorService;
    private final ObjectMapper objectMapper;
    private final TransferService transferService;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;

    public InterbankService(EventService eventService, EventExecutorService eventExecutorService, ObjectMapper objectMapper, @Lazy TransferService transferService, AccountRepository accountRepository, CurrencyRepository currencyRepository) {
        this.eventService = eventService;
        this.eventExecutorService = eventExecutorService;
        this.objectMapper = objectMapper;
        this.transferService = transferService;
        this.accountRepository = accountRepository;
        this.currencyRepository = currencyRepository;
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
                InterbankMessageDTO<InterbankTransactionDTO> properNewTx = objectMapper.convertValue(
                        messageDto,
                        objectMapper.getTypeFactory().constructParametricType(InterbankMessageDTO.class, InterbankTransactionDTO.class)
                );
                response = handleNewTXRequest(properNewTx);
                break;
            case COMMIT_TX :
                System.out.println("Received COMMIT_TX message: " + messageDto.getMessage());

                InterbankMessageDTO<CommitTransactionDTO> properCommitTx = objectMapper.convertValue(
                        messageDto,
                        objectMapper.getTypeFactory().constructParametricType(InterbankMessageDTO.class, CommitTransactionDTO.class)
                );
                try {
                    handleCommitTXRequest(properCommitTx);
                } catch (Exception e) {
                    response.setVote("NO");
                    response.setReasons(List.of(new VoteReasonDTO("COMMIT_TX_FAILED", null)));
                    return response;
                }
                response.setVote("YES");
                break;
            case ROLLBACK_TX :
                response.setVote("YES");
                break;
            default:
                throw new IllegalArgumentException("Unknown message type");

        }

        return response;
    }

    public void handleCommitTXRequest(InterbankMessageDTO<CommitTransactionDTO> messageDto) {
        Event event = eventService.findEventByIdempotenceKey(messageDto.getIdempotenceKey());
        if (event == null) {
            throw new IllegalArgumentException("Event not found for idempotence key: " + messageDto.getIdempotenceKey());
        }

        InterbankMessageDTO<InterbankTransactionDTO> originalNewTxMessage;
        try {
            originalNewTxMessage = objectMapper.readValue(
                    event.getPayload(),
                    objectMapper.getTypeFactory().constructParametricType(
                            InterbankMessageDTO.class,
                            InterbankTransactionDTO.class
                    )
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to parse NEW_TX payload: " + e.getMessage());
        }

        InterbankTransactionDTO originalMessage = originalNewTxMessage.getMessage();

        Account localAccount = null;
        Currency localCurrency = null;
        Double amount = 0.0;
        for (PostingDTO posting : originalMessage.getPostings()) {
            if (posting.getAccount().getId().getRoutingNumber() == 111) {
                String localAccountId = posting.getAccount().getId().getUserId();
                amount = posting.getAmount();
                Optional<Account> localAccountOpt = accountRepository.findByAccountNumber(localAccountId);
                if (localAccountOpt.isPresent()) {
                    localAccount = localAccountOpt.get();
                } else {
                    throw new IllegalArgumentException("Local account not found: " + localAccountId);
                }

                if (posting.getAsset() instanceof MonetaryAssetDTO) {
                    MonetaryAssetDTO asset = (MonetaryAssetDTO) posting.getAsset();
                    if (asset.getAsset() instanceof CurrencyAsset) {
                        CurrencyType currencyType = CurrencyType.fromString(((CurrencyAsset) asset.getAsset()).getCurrency());
                        Optional<Currency> currencyOpt = currencyRepository.findByCode(currencyType);
                        if (currencyOpt.isPresent()) {
                            localCurrency = currencyOpt.get();
                        } else {
                            throw new IllegalArgumentException("Currency not found: " + currencyType);
                        }
                    } else {
                        throw new IllegalArgumentException("Invalid asset type");
                    }
                } else {
                    throw new IllegalArgumentException("Invalid asset type");
                }


            }
        }

        if (localAccount == null) {
            throw new IllegalArgumentException("Local account not found");
        }

        Transfer transfer = transferService.receiveForeignBankTransfer(
                localAccount.getAccountNumber(),
                amount,
                originalMessage.getMessage(),
                "Banka 4",
                localCurrency
        );

        if (transfer == null) {
            throw new IllegalArgumentException("Failed to create transfer");
        }
    }

    public VoteDTO handleNewTXRequest(InterbankMessageDTO<InterbankTransactionDTO> messageDto) {
        // check if all postings are monetary
        VoteDTO response = new VoteDTO();
        try {

            InterbankTransactionDTO message = messageDto.getMessage();
            List<PostingDTO> postings = message.getPostings();

            String currencyCode = null;

            if (postings == null || postings.isEmpty() || postings.size() != 2) {
                response.setVote("NO");
                response.setReasons(List.of(new VoteReasonDTO("NO_POSTINGS", null)));
                return response;
            }

            String localAcountId = null;

            for (PostingDTO posting : postings) {
                if (posting.getAsset() instanceof MonetaryAssetDTO) {
                    MonetaryAssetDTO asset = (MonetaryAssetDTO) posting.getAsset();
                    if (asset.getAsset().getCurrency() == null) {
                        response.setVote("NO");
                        response.setReasons(List.of(new VoteReasonDTO("NO_SUCH_ASSET", posting)));
                        return response;
                    }

                    if (currencyCode == null) {
                        currencyCode = asset.getAsset().getCurrency();
                    } else if (!currencyCode.equals(asset.getAsset().getCurrency())) {
                        response.setVote("NO");
                        response.setReasons(List.of(new VoteReasonDTO("NO_SUCH_ASSET", posting)));
                        return response;
                    }

                    TxAccountDTO account = posting.getAccount();
                    if (account.getId() == null) {
                        response.setVote("NO");
                        response.setReasons(List.of(new VoteReasonDTO("NO_SUCH_ACCOUNT", posting)));
                        return response;
                    }

                    if (account.getId().getUserId() == null || account.getId().getUserId().isEmpty()) {
                        response.setVote("NO");
                        response.setReasons(List.of(new VoteReasonDTO("NO_SUCH_ACCOUNT", posting)));
                        return response;
                    }

                    if (account.getId().getRoutingNumber() == 111) {
                        localAcountId = account.getId().getUserId();
                    }

                    if (posting.getAmount() < 0 && account.getId().getRoutingNumber() == 111) {
                        response.setVote("NO");
                        response.setReasons(List.of(new VoteReasonDTO("UNBALANCED_TX", posting)));
                        return response;
                    }
                }
            }

            // check if the local account exists
            Optional<Account> localAccountOpt = accountRepository.findByAccountNumber(localAcountId);
            if (localAccountOpt.isEmpty()) {
                response.setVote("NO");
                response.setReasons(List.of(new VoteReasonDTO("NO_SUCH_ACCOUNT", null)));
                return response;
            }

            Account localAccount = localAccountOpt.get();

            response.setVote("YES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle new transaction request: " + e.getMessage());
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
