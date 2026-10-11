package com.ecommerce.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserServiceApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsProfileFromTokenOnFirstAccess() throws Exception {
        mockMvc.perform(get("/me").with(jwt().jwt(token -> token
                        .subject("user-1")
                        .claim("email", "ada@example.test")
                        .claim("given_name", "Ada")
                        .claim("family_name", "Lovelace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.email").value("ada@example.test"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.defaultShippingAddress").value(nullValue()));
    }

    @Test
    void updatesProfileAndDefaultShippingAddress() throws Exception {
        mockMvc.perform(put("/me")
                        .with(jwt().jwt(token -> token.subject("user-2").claim("email", "grace@example.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": " Grace ",
                                  "lastName": "Hopper",
                                  "phone": "+39 333 1234567",
                                  "defaultShippingAddress": {
                                    "recipientName": "Grace Hopper",
                                    "addressLine": "Via Po 10",
                                    "city": "Torino",
                                    "postalCode": "10123",
                                    "countryCode": "it"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.defaultShippingAddress.countryCode").value("IT"));
    }

    @Test
    void rejectsInvalidPhoneNumber() throws Exception {
        mockMvc.perform(put("/me")
                        .with(jwt().jwt(token -> token.subject("user-3")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"call me\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/admin").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }
}
