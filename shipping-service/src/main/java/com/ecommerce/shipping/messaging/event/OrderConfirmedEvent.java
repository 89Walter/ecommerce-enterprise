package com.ecommerce.shipping.messaging.event;

import com.ecommerce.shipping.messaging.IntegrationEvent;

import java.time.Instant;

/** Ricevuto dall'order-service dopo un pagamento riuscito. */
public record OrderConfirmedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        String customerId,
        ShippingAddressPayload shippingAddress
) implements IntegrationEvent {}
