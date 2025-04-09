package com.banka1.banking.services;

import com.banka1.banking.dto.OTCTransactionACKDTO;
import com.banka1.banking.models.Account;
import com.banka1.banking.repository.AccountRepository;
import com.banka1.banking.saga.OTCTransaction;
import com.banka1.banking.saga.OTCTransactionStage;
import com.banka1.common.listener.MessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OTCService {
    private final JmsTemplate jmsTemplate;
    private final MessageHelper messageHelper;
    @Value("${destination.otc.ack.trade}")
    private String destinationOtcAck;
    private final Map<String, OTCTransaction> activeTransactions = new HashMap<>();
    private final AccountRepository accountRepository;
    private final TaskScheduler taskScheduler;

    private void sendFailureMessage(String uid, String message) throws JmsException {
        jmsTemplate.convertAndSend(destinationOtcAck, messageHelper.createTextMessage(new OTCTransactionACKDTO(
                uid, true, message
        )));
    }

    private void retryableFailureMessage(String uid, String message, boolean rollback) {
        try {
            sendFailureMessage(uid, message);
            if(rollback) rollback(uid);
        } catch(JmsException jms) {
            taskScheduler.schedule(() -> retryableFailureMessage(uid, message, rollback), Instant.now().plusSeconds(5));
        }
    }

    private void nextStage(String uid, OTCTransactionStage expectedStage) {
        OTCTransaction transaction = activeTransactions.get(uid);
        if(transaction == null || transaction.getStage() != expectedStage)
            return;

        try {
            jmsTemplate.convertAndSend(destinationOtcAck, messageHelper.createTextMessage(new OTCTransactionACKDTO(
                    uid, false, ""
            )));
            transaction.nextStage();
        } catch (JmsException jms) {
            taskScheduler.schedule(() -> nextStage(uid, expectedStage), Instant.now().plusSeconds(5));
        }
    }

    public synchronized void rollback(String uid) {
        OTCTransaction transaction = activeTransactions.get(uid);
        if(transaction == null)
            return;

        switch (transaction.getStage()) {
            case INITIALIZED -> activeTransactions.remove(uid);
            case ASSETS_RESERVED -> {
                Account buyerAccount = accountRepository.findById(transaction.getBuyerAccountId()).orElseThrow();
                buyerAccount.setBalance(buyerAccount.getBalance() + transaction.getAmount());

                accountRepository.save(buyerAccount);
                activeTransactions.remove(uid);
            }
            case ASSETS_TRANSFERED, FINISHED -> {
                Account sellerAccount = accountRepository.findById(transaction.getSellerAccountId()).orElseThrow();
                Account buyerAccount = accountRepository.findById(transaction.getBuyerAccountId()).orElseThrow();

                buyerAccount.setBalance(buyerAccount.getBalance() + transaction.getAmount());
                sellerAccount.setBalance(sellerAccount.getBalance() - transaction.getAmount());

                accountRepository.save(buyerAccount);
                accountRepository.save(sellerAccount);
                activeTransactions.remove(uid);
            }
        }
    }

    public synchronized void proceed(String uid) {
        try {
            OTCTransaction transaction = activeTransactions.get(uid);
            if(transaction == null)
                throw new Exception("Invalid UID");

            log.info(transaction.getStage().toString());

            switch (transaction.getStage()) {
                case INITIALIZED -> {
                    Account account = accountRepository.findById(transaction.getBuyerAccountId()).orElseThrow();

                    if(account.getBalance() < transaction.getAmount())
                        throw new Exception("Insufficient funds");

                    account.setBalance(account.getBalance() - transaction.getAmount());
                    accountRepository.save(account);

                    nextStage(uid, transaction.getStage());
                }
                case ASSETS_RESERVED -> {
                    Account account = accountRepository.findById(transaction.getSellerAccountId()).orElseThrow();
                    account.setBalance(account.getBalance() + transaction.getAmount());
                    accountRepository.save(account);

                    nextStage(uid, transaction.getStage());
                }
                case ASSETS_TRANSFERED -> {
                    // provera konzistentnog stanja?

                    nextStage(uid, transaction.getStage());
                }
                case FINISHED -> {
                    log.info("Finished transaction " + uid);
                    activeTransactions.remove(uid);
                }
            }
        } catch(Exception e) {
            retryableFailureMessage(uid, e.getMessage(), true);
        }
    }

    public synchronized void initiate(String uid, OTCTransaction transaction) {
        try {
            if(activeTransactions.containsKey(uid))
                throw new Exception("Invalid UID");

            Account sellerAccount = accountRepository.findById(transaction.getSellerAccountId()).orElseThrow();
            Account buyerAccount = accountRepository.findById(transaction.getBuyerAccountId()).orElseThrow();

            if(sellerAccount.getCurrencyType() != buyerAccount.getCurrencyType())
                throw new Exception("Currency type mismatch");

            activeTransactions.put(uid, transaction);

            jmsTemplate.convertAndSend(destinationOtcAck, messageHelper.createTextMessage(new OTCTransactionACKDTO(
                    uid, false, ""
            )));
        } catch (Exception e) {
            retryableFailureMessage(uid, e.getMessage(), false);
        }
    }

    public void payPremium(Long fromAccountId, Long toAccountId, Double amount) {
        try {
            Account fromAccount = accountRepository.findById(fromAccountId).orElseThrow();
            Account toAccount = accountRepository.findById(toAccountId).orElseThrow();

            if(fromAccount.getCurrencyType() != toAccount.getCurrencyType())
                throw new Exception("Currency type mismatch");

            if(fromAccount.getBalance() < amount)
                throw new Exception("Insufficient funds");

            fromAccount.setBalance(fromAccount.getBalance() - amount);
            toAccount.setBalance(toAccount.getBalance() + amount);

            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            log.info("Premium paid");
        } catch(Exception e) {
            log.error("Unable to pay premium: " + e.getMessage());
        }
    }
}
