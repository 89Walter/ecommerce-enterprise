package com.ecommerce.payment.messaging.event;

import com.ecommerce.payment.messaging.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;

/** Ricevuto dall'order-service: contiene solo i campi che servono per l'addebito. */
public record OrderPlacedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        String customerId,
        BigDecimal amount,
        String currency
) implements IntegrationEvent {}
