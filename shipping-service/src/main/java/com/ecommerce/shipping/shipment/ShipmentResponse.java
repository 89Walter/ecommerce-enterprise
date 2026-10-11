package com.ecommerce.shipping.shipment;

import java.time.LocalDateTime;

public record ShipmentResponse(
        Long id,
        Long orderId,
        String customerId,
        ShipmentStatus status,
        String carrier,
        String trackingCode,
        String recipientName,
        String addressLine,
        String city,
        String postalCode,
        String countryCode,
        LocalDateTime createdAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt
) {

    static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getOrderId(),
                shipment.getCustomerId(),
                shipment.getStatus(),
                shipment.getCarrier(),
                shipment.getTrackingCode(),
                shipment.getRecipientName(),
                shipment.getAddressLine(),
                shipment.getCity(),
                shipment.getPostalCode(),
                shipment.getCountryCode(),
                shipment.getCreatedAt(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt(),
                shipment.getCancelledAt()
        );
    }
}
