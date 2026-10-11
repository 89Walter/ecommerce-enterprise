package com.ecommerce.shipping.messaging.event;

public record ShippingAddressPayload(
        String recipientName,
        String addressLine,
        String city,
        String postalCode,
        String countryCode
) {}
