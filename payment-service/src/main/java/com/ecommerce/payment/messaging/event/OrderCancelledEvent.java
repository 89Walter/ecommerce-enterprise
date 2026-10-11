package com.ecommerce.payment.messaging.event;

import com.ecommerce.payment.messaging.IntegrationEvent;

import java.time.Instant;

public record OrderCancelledEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        String customerId,
        String reason
) implements IntegrationEvent {}
