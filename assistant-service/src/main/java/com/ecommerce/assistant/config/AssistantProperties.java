package com.ecommerce.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configurazione dell'assistente, letta da application.yaml (e quindi dal .env della root).
 *
 * @param ai           parametri della chiamata a Claude
 * @param services     indirizzi dei microservizi usati come strumenti dall'agente
 * @param conversation durata e lunghezza massima delle conversazioni salvate su Redis
 */
@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(Ai ai, Services services, Conversation conversation) {

    /**
     * @param apiKey         chiave API Anthropic; se vuota l'assistente risponde 503 "non configurato"
     * @param model          modello Claude da usare
     * @param effort         quanto ragiona il modello (low, medium, high, xhigh, max): più alto = più lento e costoso
     * @param maxTokens      limite di token della singola risposta (ragionamento compreso)
     * @param maxToolRounds  quante volte, al massimo, l'agente può chiamare gli strumenti per una domanda
     * @param timeout        timeout della singola chiamata HTTP verso Anthropic
     */
    public record Ai(String apiKey, String model, String effort, long maxTokens, int maxToolRounds, Duration timeout) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Services(String catalogUrl, String orderUrl, String paymentUrl, String shipmentUrl, String userUrl,
                           Duration timeout) {}

    /**
     * @param ttl         dopo quanto tempo di inattività una conversazione viene dimenticata
     * @param maxMessages quanti messaggi (domande + risposte) vengono ricordati e rimandati al modello
     */
    public record Conversation(Duration ttl, int maxMessages) {}
}
