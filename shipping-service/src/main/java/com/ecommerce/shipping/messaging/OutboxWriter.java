package com.ecommerce.shipping.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** Va chiamato dentro la transazione che modifica i dati di dominio (altrimenti fallisce). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateId, String routingKey, IntegrationEvent event) {
        outboxEventRepository.save(new OutboxEvent(
                event.eventId(),
                aggregateId,
                routingKey,
                jsonMapper.writeValueAsString(event),
                LocalDateTime.now(clock)
        ));
    }
}
