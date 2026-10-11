package com.ecommerce.user.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sostituisce i dati modificabili del profilo; un indirizzo assente rimuove quello predefinito. */
public record UpdateProfileRequest(
        @Size(max = 100, message = "Il nome non può superare 100 caratteri.")
        String firstName,
        @Size(max = 100, message = "Il cognome non può superare 100 caratteri.")
        String lastName,
        @Pattern(regexp = "^$|^\\+?[0-9 ]{6,20}$", message = "Il telefono può contenere solo cifre, spazi e un + iniziale.")
        String phone,
        @Valid
        AddressRequest defaultShippingAddress
) {}
