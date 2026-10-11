package com.ecommerce.shipping.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * RabbitMQ può consegnare lo stesso messaggio più volte: l'eventId viene registrato nella stessa
 * transazione dell'elaborazione, così ogni evento produce i suoi effetti una sola volta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final Clock clock;

    /** @return true se l'evento è nuovo e va elaborato, false se è un duplicato già gestito. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(IntegrationEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Evento {} già elaborato: duplicato ignorato.", event.eventId());
            return false;
        }
        processedEventRepository.save(new ProcessedEvent(event.eventId(), LocalDateTime.now(clock)));
        return true;
    }
}
