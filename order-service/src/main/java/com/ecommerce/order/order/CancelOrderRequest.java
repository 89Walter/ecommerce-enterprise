package com.ecommerce.order.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(
        @NotBlank(message = "La motivazione dell'annullamento è obbligatoria.")
        @Size(max = 500, message = "La motivazione non può superare 500 caratteri.")
        String reason
) {}
