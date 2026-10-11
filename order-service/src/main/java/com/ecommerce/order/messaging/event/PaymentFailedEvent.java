package com.ecommerce.order.messaging.event;

import com.ecommerce.order.messaging.IntegrationEvent;

import java.time.Instant;

public record PaymentFailedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        Long paymentId,
        String reason
) implements IntegrationEvent {}
