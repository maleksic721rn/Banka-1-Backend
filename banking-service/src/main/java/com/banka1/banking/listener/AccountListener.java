package com.banka1.banking.listener;

import com.banka1.banking.dto.CreateAccountByEmployeeDTO;
import com.banka1.banking.dto.request.UserAccountsResponse;
import com.banka1.banking.dto.request.UserRequest;
import com.banka1.banking.models.Account;
import com.banka1.banking.services.AccountService;
import com.banka1.common.listener.MessageHelper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountListener {
    private final AccountService accountService;
    private final MessageHelper messageHelper;
    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = "${destination.account}", concurrency = "5-10")
    public void onActivationMessage(Message message) throws JMSException {
        var dto = messageHelper.getMessage(message, CreateAccountByEmployeeDTO.class);
        try {
            System.out.println("---------------------------");
            System.out.println(dto);
            if (dto != null)
                accountService.createAccount(dto.getCreateAccountDTO(), dto.getEmployeeId());
        } catch (Exception e) {
            log.error("AccountListener: ", e);
            jmsTemplate.convertAndSend(message.getJMSReplyTo(), messageHelper.createTextMessage(e.getMessage()));
            return;
        }
        jmsTemplate.convertAndSend(message.getJMSReplyTo(), messageHelper.createTextMessage("null"));
    }

    @JmsListener(destination = "${destination.account.by-user}", concurrency = "5-10")
    public void onGetAccountsByUser(Message message) throws JMSException {
        UserRequest request = messageHelper.getMessage(message, UserRequest.class);

        try {
            List<Account> accounts = accountService.getAccountsByOwnerId(request.getUserId());
            UserAccountsResponse response = new UserAccountsResponse(accounts);

            jmsTemplate.convertAndSend(message.getJMSReplyTo(), messageHelper.createTextMessage(response));
        } catch (Exception e) {
            log.error("AccountListener - Error fetching accounts for user {}: {}", request.getUserId(), e.getMessage());
            jmsTemplate.convertAndSend(message.getJMSReplyTo(), messageHelper.createTextMessage(e.getMessage()));
        }
    }

}
