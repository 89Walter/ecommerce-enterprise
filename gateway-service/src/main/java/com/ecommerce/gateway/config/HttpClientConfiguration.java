package com.ecommerce.gateway.config;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache DNS breve per il client HTTP con cui il gateway inoltra le richieste ai servizi.
 * <p>
 * Quando un container viene ricreato (per esempio con {@code docker compose up --build}) può ricevere un nuovo
 * indirizzo IP nella rete Docker. Con la cache predefinita il gateway continuerebbe a usare quello vecchio
 * per molti minuti, rispondendo 503 anche se il servizio è già ripartito. Con pochi secondi di cache il
 * nuovo indirizzo viene usato quasi subito.
 */
@Configuration
public class HttpClientConfiguration {

    @Bean
    HttpClientCustomizer shortDnsCache() {
        return httpClient -> httpClient.resolver(spec -> spec
                .cacheMaxTimeToLive(Duration.ofSeconds(5))
                .cacheNegativeTimeToLive(Duration.ofSeconds(1)));
    }
}
