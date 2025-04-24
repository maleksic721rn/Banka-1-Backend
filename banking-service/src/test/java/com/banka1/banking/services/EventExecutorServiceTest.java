package com.banka1.banking.services;

import com.banka1.banking.dto.CreateEventDeliveryDTO;
import com.banka1.banking.dto.interbank.InterbankMessageType;
import com.banka1.banking.dto.interbank.VoteDTO;
import com.banka1.banking.models.Event;
import com.banka1.banking.models.EventDelivery;
import com.banka1.banking.models.helper.DeliveryStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class EventExecutorServiceTest {

    @Mock
    private EventService eventService;

    @Mock
    private InterbankOperationService interbankService;

    @InjectMocks
    private EventExecutorService eventExecutorService;

    @Captor
    private ArgumentCaptor<CreateEventDeliveryDTO> deliveryCaptor;

    private Event mockEvent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockEvent = new Event();
        mockEvent.setId(1L);
        mockEvent.setUrl("http://localhost:8080/test");
        mockEvent.setPayload("{\"test\":\"value\"}");
        mockEvent.setMessageType(InterbankMessageType.NEW_TX);
    }

    @Test
    void testRollbackTransactionCallsInterbankService() {
        eventExecutorService.rollbackTransaction(mockEvent);
        verify(interbankService).sendRollback(mockEvent);
    }

    @Test
    void testHandleNewTxSuccess_voteYes_callsSendCommit() throws Exception {
        VoteDTO voteDTO = new VoteDTO();
        voteDTO.setVote("yes");

        String voteJson = new ObjectMapper().writeValueAsString(voteDTO);
        String wrappedJson = new ObjectMapper().writeValueAsString(voteJson);

        eventExecutorService.handleNewTxSuccess(mockEvent, wrappedJson);
        verify(interbankService).sendCommit(mockEvent);
    }

    @Test
    void testHandleNewTxSuccess_voteNo_callsSendRollback() throws Exception {
        VoteDTO voteDTO = new VoteDTO();
        voteDTO.setVote("no");

        String voteJson = new ObjectMapper().writeValueAsString(voteDTO);
        String wrappedJson = new ObjectMapper().writeValueAsString(voteJson);

        eventExecutorService.handleNewTxSuccess(mockEvent, wrappedJson);
        verify(interbankService).sendRollback(mockEvent);
    }


}
