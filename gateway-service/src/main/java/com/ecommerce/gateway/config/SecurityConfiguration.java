package com.ecommerce.gateway.config;

import com.ecommerce.gateway.security.KeycloakJwtAuthenticationConverter;
import com.ecommerce.gateway.security.PathBasedAuthenticationManagerResolver;
import com.ecommerce.gateway.security.RateLimitKeyResolver;
import com.ecommerce.gateway.security.RequiredAudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Il gateway valida il token prima di inoltrare la richiesta. Ogni microservizio ha la propria audience:
 * un token valido per il catalog ma senza audience "order-service" viene respinto già qui sugli ordini.
 * I microservizi ripetono comunque la validazione (difesa in profondità).
 */
@Configuration
public class SecurityConfiguration {

    static final String CATALOG = "/api/v1/catalog/";
    static final String ORDERS = "/api/v1/orders";
    static final String USERS = "/api/v1/users";
    static final String PAYMENTS = "/api/v1/payments";
    static final String SHIPMENTS = "/api/v1/shipments";
    static final String ASSISTANT = "/api/v1/assistant";

    @Bean
    KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    /** Usato dal filtro RequestRateLimiter (vedi application.yaml). Primary: è anche il resolver predefinito. */
    @Bean
    @Primary
    RateLimitKeyResolver rateLimitKeyResolver() {
        return new RateLimitKeyResolver();
    }

    /** Limite aggiuntivo delle rotte AI, con contatori separati da quello generale (chiavi "ai:..."). */
    @Bean
    RateLimitKeyResolver aiRateLimitKeyResolver() {
        return new RateLimitKeyResolver("ai");
    }

    @Bean
    PathBasedAuthenticationManagerResolver authenticationManagerResolver(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${security.jwt.issuer-uri}") String issuerUri,
            @Value("${security.jwt.catalog-audience}") String catalogAudience,
            @Value("${security.jwt.order-audience}") String orderAudience,
            @Value("${security.jwt.user-audience}") String userAudience,
            @Value("${security.jwt.payment-audience}") String paymentAudience,
            @Value("${security.jwt.shipping-audience}") String shippingAudience,
            @Value("${security.jwt.assistant-audience}") String assistantAudience,
            KeycloakJwtAuthenticationConverter authenticationConverter
    ) {
        Map<String, String> audiencesByPrefix = new LinkedHashMap<>();
        audiencesByPrefix.put(CATALOG, catalogAudience);
        audiencesByPrefix.put(ORDERS, orderAudience);
        audiencesByPrefix.put(USERS, userAudience);
        audiencesByPrefix.put(PAYMENTS, paymentAudience);
        audiencesByPrefix.put(SHIPMENTS, shippingAudience);
        audiencesByPrefix.put(ASSISTANT, assistantAudience);

        Map<String, JwtReactiveAuthenticationManager> managers = new LinkedHashMap<>();
        audiencesByPrefix.forEach((prefix, audience) -> {
            JwtReactiveAuthenticationManager manager =
                    new JwtReactiveAuthenticationManager(jwtDecoder(jwkSetUri, issuerUri, audience));
            manager.setJwtAuthenticationConverter(authenticationConverter);
            managers.put(prefix, manager);
        });
        return new PathBasedAuthenticationManagerResolver(managers);
    }

    @Bean
    @Order(0)
    SecurityWebFilterChain apiSecurityWebFilterChain(
            ServerHttpSecurity http,
            PathBasedAuthenticationManagerResolver authenticationManagerResolver
    ) {
        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {})
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        // Documentazione OpenAPI di tutti i servizi
                        .pathMatchers(
                                "/api/v1/*/swagger-ui/**",
                                "/api/v1/*/swagger-ui.html",
                                "/api/v1/*/swagger-ui-custom.html",
                                "/api/v1/*/v3/api-docs/**"
                        ).permitAll()
                        // Catalog: lettura pubblica, scrittura solo ADMIN
                        .pathMatchers(HttpMethod.GET, "/api/v1/catalog/products", "/api/v1/catalog/products/**").permitAll()
                        .pathMatchers("/api/v1/catalog/products", "/api/v1/catalog/products/**").hasRole("ADMIN")
                        // Area amministrativa di ogni servizio
                        .pathMatchers(
                                "/api/v1/orders/admin/**",
                                "/api/v1/users/admin/**",
                                "/api/v1/payments/admin/**",
                                "/api/v1/shipments/admin/**"
                        ).hasRole("ADMIN")
                        // Resto delle API: basta un utente autenticato
                        .pathMatchers(
                                "/api/v1/orders", "/api/v1/orders/**",
                                "/api/v1/users/**",
                                "/api/v1/payments/**",
                                "/api/v1/shipments/**",
                                "/api/v1/assistant/**"
                        ).authenticated()
                        .anyExchange().denyAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationManagerResolver(authenticationManagerResolver)
                )
                .build();
    }

    @Bean
    @Order(1)
    SecurityWebFilterChain managementSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/actuator/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyExchange().denyAll()
                )
                .build();
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain denyUnroutedRequestsSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize -> authorize.anyExchange().denyAll())
                .build();
    }

    static ReactiveJwtDecoder jwtDecoder(String jwkSetUri, String issuerUri, String audience) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new RequiredAudienceValidator(audience);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }
}
