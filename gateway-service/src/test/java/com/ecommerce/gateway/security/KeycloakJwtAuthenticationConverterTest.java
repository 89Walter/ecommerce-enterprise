package com.ecommerce.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    @Test
    void mapsKeycloakRealmRolesAndScopesToAuthorities() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject("customer-1")
                .claim("scope", "openid profile")
                .claim("realm_access", Map.of("roles", List.of("USER", "ADMIN")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        var authentication = new KeycloakJwtAuthenticationConverter().convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .contains(new SimpleGrantedAuthority("ROLE_USER"))
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .contains(new SimpleGrantedAuthority("SCOPE_openid"));
    }
}
