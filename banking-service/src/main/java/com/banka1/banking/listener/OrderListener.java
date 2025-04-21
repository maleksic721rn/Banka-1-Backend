package com.banka1.banking.listener;

import com.banka1.banking.dto.OrderTransactionInitiationDTO;
import com.banka1.banking.services.OrderService;
import com.banka1.common.listener.MessageHelper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class OrderListener {

    private final OrderService orderService;
    private final MessageHelper messageHelper;
    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = "${destination.order.init}", concurrency = "5-10")
    public void onOrderTransactionInit(Message message) throws JMSException {
        var dto = messageHelper.getMessage(message, OrderTransactionInitiationDTO.class);
        log.info("[VIDI OVO] Primljena poruka za OrderTransactionInitiationDTO: {}", dto);

        try {
            if (dto == null)
                throw new RuntimeException("DTO je null");
            orderService.processOrderTransaction(dto);
        } catch (Exception e) {
            log.error("OrderListener: ", e);
            jmsTemplate.convertAndSend(message.getJMSReplyTo(), messageHelper.createTextMessage(e.getMessage()));
            return;
        }
        jmsTemplate.convertAndSend(message.getJMSReplyTo(), messageHelper.createTextMessage("null"));

    }
}