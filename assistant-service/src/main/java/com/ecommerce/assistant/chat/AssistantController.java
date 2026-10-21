package com.ecommerce.assistant.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API REST dell'assistente AI, tutte <b>sincrone</b>. L'assistant-service non usa RabbitMQ: legge i dati
 * chiamando in modo sincrono le API degli altri servizi, con il token dell'utente.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Assistente", description = "Assistente AI per ordini e acquisti, basato su Claude.")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class AssistantController {

    private final AssistantChatService assistantChatService;

    /**
     * {@code POST /api/v1/assistant/chat} con body {@code {"message": "...", "conversationId": "..."}} —
     * una domanda all'assistente. Qualsiasi utente autenticato.
     * <p>
     * <b>SINCRONA</b>, ma lunga: la richiesta resta aperta finché l'agente non ha finito il suo ciclo, che può
     * durare decine di secondi. A ogni giro chiama l'API di Anthropic (HTTP sincrono) e, se Claude chiede uno
     * strumento, le API di catalog, ordini, pagamenti, spedizioni o profilo (HTTP sincrono, con il token
     * dell'utente). Per questo il gateway le concede 140 secondi. La conversazione è salvata su Redis.
     */
    @Operation(
            summary = "Invia un messaggio all'assistente",
            description = "Alla prima domanda ometti conversationId; per continuare la conversazione rimanda quello "
                    + "ricevuto. L'assistente consulta catalogo, ordini, pagamenti e spedizioni con i permessi "
                    + "dell'utente autenticato e non esegue azioni per suo conto.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Risposta dell'assistente."),
            @ApiResponse(responseCode = "400", description = "Messaggio non valido."),
            @ApiResponse(responseCode = "404", description = "Conversazione inesistente o scaduta."),
            @ApiResponse(responseCode = "503", description = "Assistente non configurato o modello non disponibile.")
    })
    @PostMapping("/chat")
    public ChatResponse chat(JwtAuthenticationToken authentication, @Valid @RequestBody ChatRequest request) {
        return assistantChatService.chat(
                authentication.getName(),
                authentication.getToken().getTokenValue(),
                request
        );
    }

    /**
     * {@code DELETE /api/v1/assistant/conversations/{conversationId}} — cancella una conversazione dell'utente.
     * <p>
     * <b>SINCRONA</b>: elimina la chiave da Redis; 204 se esisteva, 404 altrimenti.
     */
    @Operation(summary = "Dimentica una conversazione", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> delete(JwtAuthenticationToken authentication, @PathVariable String conversationId) {
        return assistantChatService.deleteConversation(authentication.getName(), conversationId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * {@code GET /api/v1/assistant/status} — {@code {"available": true/false}}: dice se la chiave Anthropic è configurata.
     * <p>
     * <b>SINCRONA</b> e immediata: non chiama Anthropic, controlla solo la configurazione.
     */
    @Operation(
            summary = "Stato dell'assistente",
            description = "Indica se l'assistente è configurato, così il frontend può mostrare o nascondere la chat.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("available", assistantChatService.available());
    }
}
