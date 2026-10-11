package com.ecommerce.order.messaging.event;

import com.ecommerce.order.messaging.IntegrationEvent;

import java.time.Instant;

/** Stato della spedizione: CREATED, SHIPPED, DELIVERED o CANCELLED. */
public record ShippingUpdatedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        Long shipmentId,
        String status,
        String carrier,
        String trackingCode
) implements IntegrationEvent {}
