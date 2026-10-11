package com.ecommerce.catalog.product;

import lombok.Builder;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Builder
public record ProductRequest(
        @NotBlank(message = "Il nome è obbligatorio.")
        @Size(max = 255, message = "Il nome non può superare 255 caratteri.")
        String name,
        @NotBlank(message = "La descrizione è obbligatoria.")
        String description,
        @NotNull(message = "Il prezzo è obbligatorio.")
        @Positive(message = "Il prezzo deve essere maggiore di zero.")
        @Digits(integer = 8, fraction = 2, message = "Il prezzo può avere al massimo 8 cifre intere e 2 decimali.")
        BigDecimal price
) {}
