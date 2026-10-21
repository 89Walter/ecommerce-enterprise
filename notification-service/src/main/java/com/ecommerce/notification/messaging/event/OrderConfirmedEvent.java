package com.ecommerce.notification.messaging.event;

import com.ecommerce.notification.messaging.IntegrationEvent;

import java.time.Instant;
import java.util.List;

/** Pagamento riuscito: l'ordine è confermato. Contiene email e nome del cliente presi dal token all'ordine. */
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
