package com.ecommerce.catalog.config;

import com.ecommerce.catalog.product.ProductController;
import com.ecommerce.catalog.product.ProductResponse;
import com.ecommerce.catalog.product.ProductService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProductController.class, SwaggerUiController.class})
@Import({SecurityConfiguration.class, PaginationConfiguration.class, OpenApiConfiguration.class})
class ConfigurationTest {

    private static final String ISSUER = "https://issuer.example.test/realm";
    private static final String AUDIENCE = "catalog-service";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaginationConfiguration paginationConfiguration;

    @Autowired
    private OpenApiConfiguration openApiConfiguration;

    @MockitoBean
    private ProductService productService;

    private HttpServer jwksServer;

    @AfterEach
    void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void swaggerRouteForwardsToCustomUiAndIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerAndHealthRoutesArePublicButOtherRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui-custom.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/unmatched"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/products/insertProduct"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productReadsArePublicAndWritesRequireAdmin() throws Exception {
        given(productService.findById(1L))
                .willReturn(ProductResponse.builder().id(1L).name("Test product").build());

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/products/insertProduct")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/products/insertProduct")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginationConfigurationCapsPageSizeAtOneHundred() throws Exception {
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
        paginationConfiguration.pageableSizeLimit().customize(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("size", "1000");
        MethodParameter pageableParameter = new MethodParameter(
                ConfigurationTest.class.getDeclaredMethod("pageableEndpoint", Pageable.class),
                0
        );

        Pageable pageable = (Pageable) resolver.resolveArgument(
                pageableParameter,
                new ModelAndViewContainer(),
                new ServletWebRequest(request),
                null
        );

        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    void openApiConfigurationDeclaresServiceTitleAndVersion() {
        var definition = OpenApiConfiguration.class.getAnnotation(
                io.swagger.v3.oas.annotations.OpenAPIDefinition.class
        );

        assertThat(openApiConfiguration).isNotNull();
        assertThat(definition.info().title()).isEqualTo("CATALOG-SERVICE");
        assertThat(definition.info().version()).isEqualTo("v1");
    }

    @Test
    void jwtAuthenticationConverterMapsScopesDirectRolesAndRealmRoles() {
        JwtAuthenticationConverter converter = new SecurityConfiguration().jwtAuthenticationConverter();
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("scope", "read write")
                .claim("roles", List.of("ROLE_ADMIN", "USER"))
                .claim("realm_access", Map.of("roles", List.of("CATALOG_MANAGER", 42)))
                .build();

        var authentication = converter.convert(token);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("SCOPE_read", "SCOPE_write", "ROLE_ADMIN", "ROLE_USER", "ROLE_CATALOG_MANAGER");
    }

    @Test
    void jwtAuthenticationConverterHandlesMissingAndMalformedRoleClaims() {
        JwtAuthenticationConverter converter = new SecurityConfiguration().jwtAuthenticationConverter();
        Jwt malformedRolesToken = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("roles", List.of())
                .claim("realm_access", Map.of("roles", "not-a-list"))
                .build();
        Jwt missingRolesToken = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "configuration-test")
                .build();

        var malformedRolesAuthentication = converter.convert(malformedRolesToken);
        var missingRolesAuthentication = converter.convert(missingRolesToken);

        assertThat(malformedRolesAuthentication).isNotNull();
        assertThat(malformedRolesAuthentication.getAuthorities())
                .extracting("authority")
                .containsExactly("FACTOR_BEARER");
        assertThat(missingRolesAuthentication).isNotNull();
        assertThat(missingRolesAuthentication.getAuthorities())
                .extracting("authority")
                .containsExactly("FACTOR_BEARER");
    }

    @Test
    void jwtDecoderAcceptsValidIssuerAndAudienceAndRejectsInvalidClaims() throws Exception {
        RSAKey signingKey = new RSAKeyGenerator(2048).keyID("config-test-key").generate();
        startJwksServer(signingKey);
        JwtDecoder decoder = new SecurityConfiguration().jwtDecoder(
                "http://localhost:" + jwksServer.getAddress().getPort() + "/jwks",
                ISSUER,
                AUDIENCE
        );

        assertThat(decoder.decode(createToken(signingKey, ISSUER, List.of(AUDIENCE))).getSubject())
                .isEqualTo("configuration-test");
        assertThatThrownBy(() -> decoder.decode(createToken(signingKey, ISSUER, List.of("other-service"))))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(createToken(signingKey, "https://wrong-issuer.example.test", List.of(AUDIENCE))))
                .isInstanceOf(JwtValidationException.class);
    }

    private void startJwksServer(RSAKey signingKey) throws Exception {
        jwksServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            byte[] body = ("{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        jwksServer.start();
    }

    private static String createToken(RSAKey key, String issuer, List<String> audiences) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("configuration-test")
                .audience(audiences)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims
        );
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }

    private void pageableEndpoint(Pageable pageable) {}
}
