package com.ecommerce.shipping.messaging.event;

import com.ecommerce.shipping.messaging.IntegrationEvent;

import java.time.Instant;

public record OrderCancelledEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        String customerId,
        String reason
) implements IntegrationEvent {}
