package com.banka1.banking.listener;

import com.banka1.banking.dto.OTCPremiumFeeDTO;
import com.banka1.banking.dto.OTCTransactionACKDTO;
import com.banka1.banking.dto.OTCTransactionInitiationDTO;
import com.banka1.banking.services.OTCService;
import com.banka1.common.listener.MessageHelper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OTCListener {
    private final OTCService otcService;
    private final MessageHelper messageHelper;

    @JmsListener(destination = "${destination.otc.ack.bank}", concurrency = "5-10")
    public void onAckMessage(Message message) throws JMSException {
        OTCTransactionACKDTO dto = messageHelper.getMessage(message, OTCTransactionACKDTO.class);
        if(dto.isFailure()) {
            log.error(dto.getMessage());
            otcService.rollback(dto.getUid());
        } else {
            log.info("Continuing OTC transaction " + dto.getUid());
            otcService.proceed(dto.getUid());
        }
    }

    @JmsListener(destination = "${destination.otc.init}", concurrency = "5-10")
    public void onInitMessage(Message message) throws JMSException {
        OTCTransactionInitiationDTO dto = messageHelper.getMessage(message, OTCTransactionInitiationDTO.class);
        log.info("Initiating OTC transaction " + dto.getUid());
        otcService.initiate(dto.getUid(), dto.getSellerAccountId(), dto.getBuyerAccountId(), dto.getAmount());
    }

    @JmsListener(destination = "${destination.otc.premium}", concurrency = "5-10")
    public void onPayPremiumMessage(Message message) throws JMSException {
        OTCPremiumFeeDTO dto = messageHelper.getMessage(message, OTCPremiumFeeDTO.class);

        log.info("Paying OTC transaction premium...");
        otcService.payPremium(dto.getBuyerAccountId(), dto.getSellerAccountId(), dto.getAmount());
    }
}
