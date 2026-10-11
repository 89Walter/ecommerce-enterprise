package com.ecommerce.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PathBasedAuthenticationManagerResolverTest {

    private final ReactiveAuthenticationManager catalogManager = Mono::just;
    private final ReactiveAuthenticationManager orderManager = Mono::just;
    private final PathBasedAuthenticationManagerResolver resolver = new PathBasedAuthenticationManagerResolver(Map.of(
            "/api/v1/catalog/", catalogManager,
            "/api/v1/orders", orderManager
    ));

    @Test
    void selectsTheManagerOfTheServiceThatOwnsThePath() {
        assertThat(resolve("/api/v1/catalog/products/1")).isSameAs(catalogManager);
        assertThat(resolve("/api/v1/orders")).isSameAs(orderManager);
        assertThat(resolve("/api/v1/orders/12/cancel")).isSameAs(orderManager);
    }

    @Test
    void doesNotConfusePathsThatOnlyShareAPrefix() {
        ReactiveAuthenticationManager manager = resolve("/api/v1/ordersarchive");

        assertThat(manager).isNotSameAs(orderManager);
        StepVerifier.create(manager.authenticate(new TestingAuthenticationToken("user", "token")))
                .expectError(InvalidBearerTokenException.class)
                .verify();
    }

    private ReactiveAuthenticationManager resolve(String path) {
        return resolver.resolve(MockServerWebExchange.from(MockServerHttpRequest.get(path))).block();
    }
}
