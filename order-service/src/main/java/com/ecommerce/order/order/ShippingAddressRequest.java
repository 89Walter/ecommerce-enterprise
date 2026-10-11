package com.ecommerce.order.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
        @NotBlank(message = "Il destinatario è obbligatorio.")
        @Size(max = 120, message = "Il destinatario non può superare 120 caratteri.")
        String recipientName,
        @NotBlank(message = "L'indirizzo è obbligatorio.")
        @Size(max = 255, message = "L'indirizzo non può superare 255 caratteri.")
        String addressLine,
        @NotBlank(message = "La città è obbligatoria.")
        @Size(max = 120, message = "La città non può superare 120 caratteri.")
        String city,
        @NotBlank(message = "Il CAP è obbligatorio.")
        @Size(max = 20, message = "Il CAP non può superare 20 caratteri.")
        String postalCode,
        @NotBlank(message = "Il codice paese è obbligatorio.")
        @Pattern(regexp = "[A-Za-z]{2}", message = "Il codice paese deve contenere due lettere.")
        String countryCode
) {}
