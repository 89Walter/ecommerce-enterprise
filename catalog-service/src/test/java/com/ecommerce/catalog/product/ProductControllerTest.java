package com.ecommerce.catalog.product;

import com.ecommerce.catalog.config.SecurityConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfiguration.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void createsAProduct() throws Exception {
        given(productService.create(any())).willReturn(
                ProductResponse.builder()
                        .id(1L)
                        .name("Keyboard")
                        .description("Mechanical keyboard")
                        .price(new BigDecimal("49.90"))
                        .createdAt(LocalDateTime.parse("2026-10-09T10:00:00"))
                        .build()
        );

        mockMvc.perform(post("/products/insertProduct")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Keyboard","description":"Mechanical keyboard","price":49.90}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Keyboard"));
    }

    @Test
    void rejectsInvalidProductInput() throws Exception {
        mockMvc.perform(post("/products/insertProduct")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","description":"Invalid","price":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsProductInputWhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/products/insertProduct")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Richiesta non valida."))
                .andExpect(jsonPath("$.errors.name").value("Il nome è obbligatorio."))
                .andExpect(jsonPath("$.errors.description").value("La descrizione è obbligatoria."))
                .andExpect(jsonPath("$.errors.price").value("Il prezzo è obbligatorio."));
    }

    @Test
    void rejectsZeroOrNegativeProductPrice() throws Exception {
        for (String price : List.of("0", "-1")) {
            mockMvc.perform(post("/products/insertProduct")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Keyboard","description":"Mechanical keyboard","price":%s}
                                    """.formatted(price)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.price")
                            .value("Il prezzo deve essere maggiore di zero."));
        }
    }

    @Test
    void findsAProductById() throws Exception {
        given(productService.findById(1L)).willReturn(
                ProductResponse.builder().id(1L).name("Keyboard").price(new BigDecimal("49.90")).build()
        );

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Keyboard"));
    }

    @Test
    void updatesAProduct() throws Exception {
        given(productService.update(eq(1L), any())).willReturn(
                ProductResponse.builder()
                        .id(1L)
                        .name("Updated keyboard")
                        .price(new BigDecimal("59.90"))
                        .build()
        );

        mockMvc.perform(put("/products/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated keyboard","description":"Updated description","price":59.90}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated keyboard"))
                .andExpect(jsonPath("$.price").value(59.90));
    }

    @Test
    void rejectsProductCreationWithoutAdminRole() throws Exception {
        mockMvc.perform(post("/products/insertProduct")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Keyboard","description":"Mechanical keyboard","price":49.90}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsProductCreationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/products/insertProduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Keyboard","description":"Mechanical keyboard","price":49.90}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mapsAdminRoleFromJwtClaim() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "catalog-admin")
                .claim("realm_access", java.util.Map.of("roles", List.of("ADMIN")))
                .build();

        var authentication = new SecurityConfiguration()
                .jwtAuthenticationConverter()
                .convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN");
    }

    @Test
    void deletesAProduct() throws Exception {
        mockMvc.perform(delete("/products/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void listsProductsWithSearchAndPagination() throws Exception {
        ProductResponse product = ProductResponse.builder()
                .id(1L)
                .name("Keyboard")
                .price(new BigDecimal("49.90"))
                .createdAt(LocalDateTime.parse("2026-10-09T10:00:00"))
                .build();
        given(productService.findAll(eq("keyboard"), any()))
                .willReturn(new PageImpl<>(List.of(product), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/products")
                        .param("search", "keyboard")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Keyboard"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(5));
    }
}
