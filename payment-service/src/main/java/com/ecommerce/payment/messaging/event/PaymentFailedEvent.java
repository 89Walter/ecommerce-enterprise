package com.ecommerce.payment.messaging.event;

import com.ecommerce.payment.messaging.IntegrationEvent;

import java.time.Instant;

public record PaymentFailedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        Long paymentId,
        String reason
) implements IntegrationEvent {}
