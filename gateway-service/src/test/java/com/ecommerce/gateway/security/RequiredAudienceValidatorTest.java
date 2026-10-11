package com.ecommerce.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredAudienceValidatorTest {

    @Test
    void acceptsTokenWhenRequiredServiceAudienceIsPresent() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject("customer-1")
                .audience(List.of("catalog-service", "order-service"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(new RequiredAudienceValidator("order-service").validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWhenRequiredServiceAudienceIsMissing() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .subject("customer-1")
                .audience(List.of("catalog-service"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(new RequiredAudienceValidator("order-service").validate(jwt).hasErrors()).isTrue();
    }
}
