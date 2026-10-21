package com.ecommerce.order.messaging.event;

import com.ecommerce.order.messaging.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Pubblicato quando un ordine viene creato: il payment-service avvia l'addebito. */
public record OrderPlacedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        String customerId,
        String customerEmail,
        String customerName,
        BigDecimal amount,
        String currency,
        List<OrderLinePayload> items
) implements IntegrationEvent {}
