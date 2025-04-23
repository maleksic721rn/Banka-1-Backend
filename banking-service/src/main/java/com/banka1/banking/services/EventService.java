package com.banka1.banking.services;

import com.banka1.banking.config.InterbankConfig;
import com.banka1.banking.dto.CreateEventDTO;
import com.banka1.banking.dto.CreateEventDeliveryDTO;
import com.banka1.banking.dto.interbank.InterbankMessageDTO;
import com.banka1.banking.models.Event;
import com.banka1.banking.models.EventDelivery;
import com.banka1.banking.models.helper.DeliveryStatus;
import com.banka1.banking.models.helper.IdempotenceKey;
import com.banka1.banking.models.interbank.EventDirection;
import com.banka1.banking.repository.EventDeliveryRepository;
import com.banka1.banking.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventDeliveryRepository eventDeliveryRepository;
    private final InterbankConfig config;

    public int attemptCount(Event event) {
        return event.getDeliveries().size();
    }

    public void changeEventStatus(Event event, DeliveryStatus status) {
        event.setStatus(status);
        eventRepository.save(event);
    }

    public Event receiveEvent(InterbankMessageDTO<?> dto, String rawPayload, String sourceUrl) {

        Event event = new Event();

        if (dto == null) {
            return null;
        }

        if (eventRepository.existsByIdempotenceKey(dto.getIdempotenceKey())) {
            throw new RuntimeException("Event already exists");
        }

        try {
            event.setMessageType(dto.getMessageType());
            event.setPayload(rawPayload);
            event.setUrl(sourceUrl);

            event.setIdempotenceKey(dto.getIdempotenceKey());
            event.setDirection(EventDirection.INCOMING);
            event.setStatus(DeliveryStatus.PENDING);
        } catch (Exception e) {
            event.setMessageType(null);
            event.setPayload(rawPayload);
            event.setUrl(sourceUrl);
            if (dto.getIdempotenceKey() != null && dto.getIdempotenceKey().getRoutingNumber() != null && dto.getIdempotenceKey().getLocallyGeneratedKey() != null) {
                event.setIdempotenceKey(dto.getIdempotenceKey());
            } else {
                IdempotenceKey idempotenceKey = new IdempotenceKey();
                idempotenceKey.setRoutingNumber(config.getRoutingNumber());
                idempotenceKey.setLocallyGeneratedKey(UUID.randomUUID().toString());
                event.setIdempotenceKey(idempotenceKey);
            }
            event.setDirection(EventDirection.INCOMING);
            event.setStatus(DeliveryStatus.FAILED);

            throw new RuntimeException("Failed to create event: " + e.getMessage());
        }


        System.out.println("Saving event with idempotence key: " + event.getIdempotenceKey().getRoutingNumber() + " - " + event.getIdempotenceKey().getLocallyGeneratedKey());
        return eventRepository.save(event);
    }

    @Transactional
    public Event createEvent(CreateEventDTO createEventDTO) {
        Event event = new Event();
        event.setPayload(createEventDTO.getPayload());
        event.setUrl(createEventDTO.getUrl());
        event.setMessageType(createEventDTO.getMessage().getMessageType());
        event.setDirection(EventDirection.OUTGOING);

        event.setIdempotenceKey(createEventDTO.getMessage().getIdempotenceKey());

        Event saved = eventRepository.save(event);
        return saved;
    }

    public EventDelivery createEventDelivery(CreateEventDeliveryDTO createEventDeliveryDTO) {

        EventDelivery eventDelivery = new EventDelivery();
        eventDelivery.setEvent(createEventDeliveryDTO.getEvent());
        eventDelivery.setStatus(createEventDeliveryDTO.getStatus());
        eventDelivery.setHttpStatus(createEventDeliveryDTO.getHttpStatus());
        eventDelivery.setDurationMs(createEventDeliveryDTO.getDurationMs());
        eventDelivery.setResponseBody(createEventDeliveryDTO.getResponseBody());

        eventDelivery.setSentAt(Instant.now());

        System.out.println("Event delivery created: " + eventDelivery.getResponseBody());

        return eventDeliveryRepository.save(eventDelivery);
    }

    public List<EventDelivery> getEventDeliveriesForEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        return eventDeliveryRepository.findByEvent(event);
    }

    public Event findEventByIdempotenceKey(IdempotenceKey idempotenceKey) {
        return eventRepository.findByIdempotenceKey(idempotenceKey)
                .orElseThrow(() -> new RuntimeException("Event not found"));
    }

}
