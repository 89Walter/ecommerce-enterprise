package com.ecommerce.order.order;

public record ShippingAddressResponse(
        String recipientName,
        String addressLine,
        String city,
        String postalCode,
        String countryCode
) {}
