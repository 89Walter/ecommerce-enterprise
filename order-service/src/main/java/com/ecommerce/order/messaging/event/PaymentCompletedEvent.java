package com.ecommerce.order.messaging.event;

import com.ecommerce.order.messaging.IntegrationEvent;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCompletedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        String currency,
        String transactionReference
) implements IntegrationEvent {}
