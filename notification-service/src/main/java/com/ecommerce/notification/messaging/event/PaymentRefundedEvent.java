package com.ecommerce.notification.messaging.event;

import com.ecommerce.notification.messaging.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRefundedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        String currency,
        String refundReference
) implements IntegrationEvent {}
