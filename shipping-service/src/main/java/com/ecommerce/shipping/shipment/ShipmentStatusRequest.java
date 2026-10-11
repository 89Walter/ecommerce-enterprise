package com.ecommerce.shipping.shipment;

import jakarta.validation.constraints.NotNull;

public record ShipmentStatusRequest(
        @NotNull(message = "Il nuovo stato è obbligatorio.")
        ShipmentStatus status
) {}
