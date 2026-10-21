package com.ecommerce.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyResolverTest {

    private final RateLimitKeyResolver resolver = new RateLimitKeyResolver();

    @Test
    void usesTheAuthenticatedUserAsKey() {
        ServerWebExchange exchange = exchangeFrom("203.0.113.7")
                .mutate()
                .principal(Mono.just(new TestingAuthenticationToken("user-123", null)))
                .build();

        assertThat(resolver.resolve(exchange).block()).isEqualTo("user:user-123");
    }

    @Test
    void scopedResolverKeepsSeparateCountersForTheSameUser() {
        ServerWebExchange exchange = exchangeFrom("203.0.113.7")
                .mutate()
                .principal(Mono.just(new TestingAuthenticationToken("user-123", null)))
                .build();

        assertThat(new RateLimitKeyResolver("ai").resolve(exchange).block()).isEqualTo("ai:user:user-123");
        assertThat(new RateLimitKeyResolver("ai").resolve(exchangeFrom("203.0.113.7")).block()).isEqualTo("ai:ip:203.0.113.7");
    }

    @Test
    void usesTheClientIpForAnonymousRequests() {
        assertThat(resolver.resolve(exchangeFrom("203.0.113.7")).block()).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void ignoresTheSpoofableForwardedForHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/catalog/products")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 50000))
                .header("X-Forwarded-For", "198.51.100.1"));

        assertThat(resolver.resolve(exchange).block()).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void fallsBackToASharedKeyWhenTheAddressIsUnknown() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/catalog/products"));

        assertThat(resolver.resolve(exchange).block()).isEqualTo(RateLimitKeyResolver.UNKNOWN_CLIENT);
    }

    private static MockServerWebExchange exchangeFrom(String ip) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/catalog/products")
                .remoteAddress(new InetSocketAddress(ip, 50000)));
    }
}
