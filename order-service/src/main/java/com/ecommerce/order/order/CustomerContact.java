package com.ecommerce.order.order;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Email e nome del cliente, letti dal token al momento dell'ordine e salvati sull'ordine.
 * Viaggiano negli eventi, così il notification-service può scrivere al cliente senza
 * dover interrogare Keycloak o lo user-service.
 */
public record CustomerContact(String email, String name) {

    public static final CustomerContact UNKNOWN = new CustomerContact(null, null);

    public static CustomerContact from(Jwt jwt) {
        String name = String.join(" ", nonBlank(jwt.getClaimAsString("given_name")), nonBlank(jwt.getClaimAsString("family_name"))).trim();
        if (name.isEmpty()) {
            name = nonBlank(jwt.getClaimAsString("preferred_username"));
        }
        String email = nonBlank(jwt.getClaimAsString("email"));
        return new CustomerContact(email.isEmpty() ? null : email, name.isEmpty() ? null : name);
    }

    private static String nonBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
