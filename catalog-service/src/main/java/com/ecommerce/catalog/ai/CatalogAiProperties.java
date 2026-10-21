package com.ecommerce.catalog.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configurazione del generatore di descrizioni (application.yaml, valori dal .env della root).
 *
 * @param apiKey    chiave API Anthropic; se vuota la funzione risponde 503 "non configurata"
 * @param model     modello Claude da usare
 * @param effort    quanto ragiona il modello: per un testo breve basta "low"
 * @param maxTokens limite di token della risposta (ragionamento compreso)
 * @param timeout   timeout della chiamata verso Anthropic
 */
@ConfigurationProperties(prefix = "catalog.ai")
public record CatalogAiProperties(String apiKey, String model, String effort, long maxTokens, Duration timeout) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
