package com.banka1.banking.listener;

import com.banka1.banking.dto.OrderTransactionInitiationDTO;
import com.banka1.banking.dto.TaxCollectionDTO;
import com.banka1.banking.services.TaxService;
import com.banka1.common.listener.MessageHelper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaxListener {
    private final TaxService taxService;
    private final MessageHelper messageHelper;
    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = "${destination.tax}", concurrency = "5-10")
    public void onOrderTransactionInit(Message message) throws JMSException {
        var dto = messageHelper.getMessage(message, TaxCollectionDTO.class);
        log.info("Primljena poruka za TaxCollectionDTO: {}", dto);

        try {
            if (dto == null)
                throw new RuntimeException("DTO null");
            taxService.payTax(dto);
        } catch (Exception e) {
            log.error("TaxListener: ", e);
            jmsTemplate.convertAndSend(message.getJMSReplyTo(), messageHelper.createTextMessage(e.getMessage()));
            return;
        }
        jmsTemplate.convertAndSend(message.getJMSReplyTo(), messageHelper.createTextMessage("null"));

    }
}
