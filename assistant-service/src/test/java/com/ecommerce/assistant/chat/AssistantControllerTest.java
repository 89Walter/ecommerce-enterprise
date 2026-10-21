package com.ecommerce.assistant.chat;

import com.ecommerce.assistant.config.SecurityConfiguration;
import com.ecommerce.assistant.exception.AiNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssistantController.class)
@Import(SecurityConfiguration.class)
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssistantChatService assistantChatService;

    @Test
    void answersAuthenticatedUsersWithTheirIdentity() throws Exception {
        given(assistantChatService.chat(eq("user-1"), any(), any()))
                .willReturn(new ChatResponse("6f1c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f", "Ciao!", List.of()));

        mockMvc.perform(post("/chat")
                        .with(jwt().jwt(token -> token.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Ciao\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Ciao!"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"Ciao\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesTheMessage() throws Exception {
        mockMvc.perform(post("/chat")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \" \", \"conversationId\": \"not-an-id\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists())
                .andExpect(jsonPath("$.errors.conversationId").exists());
    }

    @Test
    void reportsMissingApiKeyAsServiceUnavailable() throws Exception {
        given(assistantChatService.chat(any(), any(), any())).willThrow(new AiNotConfiguredException());

        mockMvc.perform(post("/chat")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Ciao\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ANTHROPIC_API_KEY")));
    }

    @Test
    void exposesAvailabilityForTheFrontend() throws Exception {
        given(assistantChatService.available()).willReturn(false);

        mockMvc.perform(get("/status").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
