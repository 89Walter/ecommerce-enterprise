package com.ecommerce.catalog.ai;

import com.ecommerce.catalog.config.SecurityConfiguration;
import com.ecommerce.catalog.exception.ProductNotFoundException;
import com.ecommerce.catalog.product.ProductResponse;
import com.ecommerce.catalog.product.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DescriptionSuggestionController.class)
@Import(SecurityConfiguration.class)
class DescriptionSuggestionControllerTest {

    private static final ProductResponse KEYBOARD = ProductResponse.builder()
            .id(1L).name("Tastiera meccanica").description("Switch rossi.").price(new BigDecimal("89.90")).build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductDescriptionWriter productDescriptionWriter;

    @Test
    void adminReceivesASuggestionNextToTheCurrentDescription() throws Exception {
        given(productService.findById(1L)).willReturn(KEYBOARD);
        given(productDescriptionWriter.suggestDescription(KEYBOARD)).willReturn("Una tastiera meccanica per chi scrive molto.");

        mockMvc.perform(post("/products/1/description-suggestion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.currentDescription").value("Switch rossi."))
                .andExpect(jsonPath("$.suggestedDescription").value("Una tastiera meccanica per chi scrive molto."));
    }

    @Test
    void customersCannotGenerateDescriptions() throws Exception {
        mockMvc.perform(post("/products/1/description-suggestion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(productDescriptionWriter, never()).suggestDescription(any());
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        given(productService.findById(99L)).willThrow(new ProductNotFoundException(99L));

        mockMvc.perform(post("/products/99/description-suggestion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingApiKeyIsServiceUnavailable() throws Exception {
        given(productService.findById(1L)).willReturn(KEYBOARD);
        given(productDescriptionWriter.suggestDescription(KEYBOARD)).willThrow(new AiNotConfiguredException());

        mockMvc.perform(post("/products/1/description-suggestion")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ANTHROPIC_API_KEY")));
    }
}
