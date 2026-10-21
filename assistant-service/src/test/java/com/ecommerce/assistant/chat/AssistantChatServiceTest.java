package com.ecommerce.assistant.chat;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.ecommerce.assistant.config.AssistantProperties;
import com.ecommerce.assistant.conversation.ChatTurn;
import com.ecommerce.assistant.conversation.ConversationStore;
import com.ecommerce.assistant.exception.ConversationNotFoundException;
import com.ecommerce.assistant.tools.ShopApiClient;
import com.ecommerce.assistant.tools.ShopApiClient.ApiResult;
import com.ecommerce.assistant.tools.ShopTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Il modello è simulato: le risposte di Claude sono JSON nel formato reale dell'API. */
class AssistantChatServiceTest {

    private static final String TOKEN = "user-token";

    private ClaudeClient claudeClient;
    private ShopApiClient shopApiClient;
    private ConversationStore conversationStore;
    private AssistantChatService service;

    @BeforeEach
    void setUp() {
        AssistantProperties properties = new AssistantProperties(
                new AssistantProperties.Ai("test-key", "claude-opus-5-5", "low", 16000, 3, Duration.ofSeconds(10)),
                new AssistantProperties.Services("http://catalog.test/api/v1/catalog", "http://order.test/api/v1/orders",
                        "http://payment.test/api/v1/payments", "http://shipment.test/api/v1/shipments",
                        "http://user.test/api/v1/users", Duration.ofSeconds(2)),
                new AssistantProperties.Conversation(Duration.ofHours(2), 20)
        );
        claudeClient = mock(ClaudeClient.class);
        shopApiClient = mock(ShopApiClient.class);
        conversationStore = mock(ConversationStore.class);
        service = new AssistantChatService(claudeClient, new ShopTools(shopApiClient, properties), conversationStore, properties);
    }

    @Test
    void usesToolsWithTheUserTokenAndReturnsTheFinalAnswer() {
        given(claudeClient.send(any()))
                .willReturn(message("""
                        [{"type": "tool_use", "id": "toolu_1", "name": "search_products", "input": {"query": "caffè"}}]
                        """, "tool_use"))
                .willReturn(message("""
                        [{"type": "text", "text": "Ti consiglio la Macchina per capsule Mini (#37) a 79,00 €."}]
                        """, "end_turn"));
        given(shopApiClient.get(any(), eq(TOKEN)))
                .willReturn(new ApiResult(true, "{\"content\":[{\"id\":37,\"name\":\"Macchina per capsule Mini\"}]}"));

        ChatResponse response = service.chat("user-1", TOKEN, new ChatRequest("Cerco una macchina per il caffè", null));

        assertThat(response.reply()).contains("Macchina per capsule Mini");
        assertThat(response.toolsUsed()).containsExactly("search_products");
        assertThat(response.conversationId()).hasSize(36);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(shopApiClient).get(uri.capture(), eq(TOKEN));
        assertThat(uri.getValue().toString())
                .startsWith("http://catalog.test/api/v1/catalog/products")
                .contains("search=caff%C3%A8");

        // Il secondo giro rimanda la cronologia in coda: domanda, risposta del modello, risultato dello strumento.
        ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(claudeClient, times(2)).send(params.capture());
        List<MessageParam> secondRound = params.getAllValues().get(1).messages();
        assertThat(secondRound).hasSize(3);
        // Si verifica il JSON che parte davvero verso l'API: la risposta del modello torna con ruolo "assistant".
        assertThat(json(secondRound.get(1)))
                .contains("\"role\":\"assistant\"")
                .contains("\"type\":\"tool_use\"")
                .contains("\"id\":\"toolu_1\"");
        ContentBlockParam toolResult = secondRound.get(2).content().asBlockParams().get(0);
        assertThat(toolResult.asToolResult().toolUseId()).isEqualTo("toolu_1");

        verify(conversationStore).append(eq("user-1"), eq(response.conversationId()), eq(List.of()),
                eq(ChatTurn.user("Cerco una macchina per il caffè")), eq(ChatTurn.assistant(response.reply())));
    }

    @Test
    void sendsModelEffortToolsAndServerSideFallback() {
        given(claudeClient.send(any())).willReturn(message("[{\"type\": \"text\", \"text\": \"Ciao!\"}]", "end_turn"));

        service.chat("user-1", TOKEN, new ChatRequest("Ciao", null));

        ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(claudeClient).send(params.capture());
        MessageCreateParams sent = params.getValue();
        assertThat(sent.model().asString()).isEqualTo("claude-opus-5-5");
        assertThat(sent.tools()).hasValueSatisfying(tools -> assertThat(tools).hasSize(7));
        assertThat(sent._additionalBodyProperties()).containsKey("fallbacks");
        assertThat(sent._headers().values("anthropic-beta")).contains("server-side-fallback-2026-07-01");
    }

    @Test
    void continuesAnExistingConversationWithItsHistory() {
        List<ChatTurn> history = List.of(ChatTurn.user("Dov'è il mio ordine 12?"), ChatTurn.assistant("È stato spedito."));
        String conversationId = "6f1c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f";
        given(conversationStore.find("user-1", conversationId)).willReturn(Optional.of(history));
        given(claudeClient.send(any())).willReturn(message("[{\"type\": \"text\", \"text\": \"Con SDA.\"}]", "end_turn"));

        service.chat("user-1", TOKEN, new ChatRequest("Con quale corriere?", conversationId));

        ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(claudeClient).send(params.capture());
        assertThat(params.getValue().messages()).hasSize(3);
    }

    @Test
    void rejectsUnknownOrExpiredConversations() {
        String conversationId = "6f1c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f";
        given(conversationStore.find("user-1", conversationId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.chat("user-1", TOKEN, new ChatRequest("Ciao", conversationId)))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(claudeClient, never()).send(any());
    }

    @Test
    void answersPolitelyWhenTheModelRefuses() {
        given(claudeClient.send(any())).willReturn(message("[]", "refusal"));

        ChatResponse response = service.chat("user-1", TOKEN, new ChatRequest("...", null));

        assertThat(response.reply()).isEqualTo(AssistantPrompt.REFUSAL_REPLY);
    }

    @Test
    void stopsAfterTooManyToolRounds() {
        given(claudeClient.send(any())).willReturn(message("""
                [{"type": "tool_use", "id": "toolu_x", "name": "list_my_orders", "input": {}}]
                """, "tool_use"));
        given(shopApiClient.get(any(), eq(TOKEN))).willReturn(new ApiResult(true, "{\"content\":[]}"));

        ChatResponse response = service.chat("user-1", TOKEN, new ChatRequest("I miei ordini?", null));

        assertThat(response.reply()).isEqualTo(AssistantPrompt.TOO_MANY_STEPS_REPLY);
        verify(claudeClient, times(3)).send(any());
    }

    @Test
    void reportsInvalidToolInputToTheModelAsAnError() {
        given(claudeClient.send(any()))
                .willReturn(message("""
                        [{"type": "tool_use", "id": "toolu_2", "name": "get_order", "input": {"order_id": -4}}]
                        """, "tool_use"))
                .willReturn(message("[{\"type\": \"text\", \"text\": \"Numero non valido.\"}]", "end_turn"));

        service.chat("user-1", TOKEN, new ChatRequest("Ordine -4?", null));

        ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(claudeClient, times(2)).send(params.capture());
        ContentBlockParam toolResult = params.getAllValues().get(1).messages().get(2).content().asBlockParams().get(0);
        assertThat(toolResult.asToolResult().isError()).contains(true);
        verify(shopApiClient, never()).get(any(), any());
    }

    private static String json(Object value) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Message message(String contentJson, String stopReason) {
        String json = """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
                 "content": %s, "stop_reason": "%s", "stop_sequence": null,
                 "usage": {"input_tokens": 10, "output_tokens": 5}}
                """.formatted(contentJson, stopReason);
        try {
            return ObjectMappers.jsonMapper().readValue(json, Message.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
