package com.ecommerce.order.messaging.event;

import com.ecommerce.order.messaging.IntegrationEvent;

import java.time.Instant;

/** Pubblicato quando un ordine viene annullato: pagamento rimborsato e spedizione annullata. */
public record OrderCancelledEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        String customerId,
        String customerEmail,
        String customerName,
        String reason
) implements IntegrationEvent {}
