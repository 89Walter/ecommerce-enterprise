package com.ecommerce.order.messaging.event;

import com.ecommerce.order.messaging.IntegrationEvent;

import java.time.Instant;
import java.util.List;

/** Pubblicato quando il pagamento è riuscito: lo shipping-service prepara la spedizione. */
public record OrderConfirmedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        String customerId,
        String customerEmail,
        String customerName,
        ShippingAddressPayload shippingAddress,
        List<OrderLinePayload> items
) implements IntegrationEvent {}
