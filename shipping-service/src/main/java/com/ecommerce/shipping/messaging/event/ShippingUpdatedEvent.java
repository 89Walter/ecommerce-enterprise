package com.ecommerce.shipping.messaging.event;

import com.ecommerce.shipping.messaging.IntegrationEvent;

import java.time.Instant;

/** Pubblicato a ogni cambio di stato della spedizione: CREATED, SHIPPED, DELIVERED o CANCELLED. */
public record ShippingUpdatedEvent(
        String eventId,
        Instant occurredAt,
        Long orderId,
        Long shipmentId,
        String status,
        String carrier,
        String trackingCode
) implements IntegrationEvent {}
