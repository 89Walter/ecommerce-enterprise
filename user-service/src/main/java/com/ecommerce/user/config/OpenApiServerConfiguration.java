package com.ecommerce.user.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Indirizzo e titolo della documentazione Swagger.
 * <p>
 * Senza questa configurazione springdoc calcola l'indirizzo del server dalla richiesta ricevuta: dentro Docker
 * diventa il nome interno del container (es. http://user-service:...), che il browser non sa raggiungere,
 * e "Try it out" fallisce. Un indirizzo <b>relativo</b> (il solo context path) viene invece risolto dal browser
 * rispetto alla pagina aperta: funziona sia dal gateway (localhost:8080) sia avviando il servizio da IntelliJ.
 */
@Configuration
public class OpenApiServerConfiguration {

    @Bean
    OpenApiCustomizer relativeServerUrl(@Value("${server.servlet.context-path:/}") String contextPath) {
        return openApi -> {
            openApi.setServers(List.of(new Server()
                    .url(contextPath)
                    .description("Stesso indirizzo della pagina (gateway o servizio)")));
            if (openApi.getInfo() == null || "OpenAPI definition".equals(openApi.getInfo().getTitle())) {
                openApi.setInfo(new Info().title("Profili cliente").version("v1"));
            }
        };
    }
}
