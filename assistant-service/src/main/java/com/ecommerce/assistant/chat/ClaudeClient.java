package com.ecommerce.assistant.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.ecommerce.assistant.config.AssistantProperties;
import com.ecommerce.assistant.exception.AiNotConfiguredException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Unico punto di contatto con l'API di Anthropic.
 * <p>
 * La chiave viene passata in modo esplicito (non letta dalle variabili d'ambiente) perché in locale
 * arriva dal .env tramite Spring, che non la trasforma in una variabile d'ambiente del processo.
 * Senza chiave il client non viene creato e ogni richiesta risponde "assistente non configurato".
 */
@Component
public class ClaudeClient {

    private final AnthropicClient client;

    public ClaudeClient(AssistantProperties properties) {
        AssistantProperties.Ai ai = properties.ai();
        this.client = ai.configured()
                ? AnthropicOkHttpClient.builder()
                        .apiKey(ai.apiKey().trim())
                        .timeout(ai.timeout())
                        .maxRetries(2)
                        .build()
                : null;
    }

    public boolean configured() {
        return client != null;
    }

    public Message send(MessageCreateParams params) {
        if (client == null) {
            throw new AiNotConfiguredException();
        }
        return client.messages().create(params);
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
