package com.ecommerce.order.order;

import com.ecommerce.order.config.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Le richieste usano il context path reale, così si prova anche l'endpoint esattamente su /api/v1/orders. */
@WebMvcTest(OrderController.class)
@Import(SecurityConfiguration.class)
class OrderControllerTest {

    private static final String CONTEXT_PATH = "/api/v1/orders";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createsOrderAtTheContextRootForTheAuthenticatedCustomer() throws Exception {
        given(orderService.create(eq("customer-1"), any(CreateOrderRequest.class))).willReturn(response());

        mockMvc.perform(post(CONTEXT_PATH).contextPath(CONTEXT_PATH)
                        .with(jwt().jwt(token -> token.subject("customer-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [{ "productId": 7, "quantity": 2 }],
                                  "shippingAddress": {
                                    "recipientName": "Ada Lovelace", "addressLine": "Via Roma 1",
                                    "city": "Torino", "postalCode": "10100", "countryCode": "IT"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    void listsOrdersAtTheContextRoot() throws Exception {
        given(orderService.findAllForCustomer(eq("customer-1"), any())).willReturn(Page.empty());

        mockMvc.perform(get(CONTEXT_PATH).contextPath(CONTEXT_PATH)
                        .with(jwt().jwt(token -> token.subject("customer-1"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidOrderPayload() throws Exception {
        mockMvc.perform(post(CONTEXT_PATH).contextPath(CONTEXT_PATH)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists())
                .andExpect(jsonPath("$.errors.shippingAddress").exists());
    }

    @Test
    void adminListingRequiresAdminRole() throws Exception {
        given(orderService.findAll(any(), any())).willReturn(Page.empty());

        mockMvc.perform(get(CONTEXT_PATH + "/admin").contextPath(CONTEXT_PATH)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CONTEXT_PATH + "/admin").contextPath(CONTEXT_PATH)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(CONTEXT_PATH).contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());
    }

    private static OrderResponse response() {
        return new OrderResponse(1L, "customer-1", OrderStatus.PLACED, List.of(), new BigDecimal("46.80"),
                null, null, null, null, null, null, null, null, null);
    }
}
