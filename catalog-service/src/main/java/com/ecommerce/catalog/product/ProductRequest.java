package com.ecommerce.catalog.product;

import lombok.Builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Builder
public record ProductRequest(
        @NotBlank(message = "Il nome è obbligatorio.")
        String name,
        @NotBlank(message = "La descrizione è obbligatoria.")
        String description,
        @NotNull(message = "Il prezzo è obbligatorio.")
        @Positive(message = "Il prezzo deve essere maggiore di zero.")
        BigDecimal price
) {}
