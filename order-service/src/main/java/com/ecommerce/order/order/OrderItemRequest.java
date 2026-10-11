package com.ecommerce.order.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull(message = "L'ID prodotto è obbligatorio.")
        @Positive(message = "L'ID prodotto deve essere maggiore di zero.")
        Long productId,
        @Min(value = 1, message = "La quantità minima è 1.")
        @Max(value = 99, message = "La quantità massima per prodotto è 99.")
        int quantity
) {}
