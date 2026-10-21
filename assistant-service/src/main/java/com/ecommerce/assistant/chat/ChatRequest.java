package com.ecommerce.assistant.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param message        la domanda del cliente
 * @param conversationId assente alla prima domanda; poi quello restituito dalla risposta precedente
 */
public record ChatRequest(
        @NotBlank(message = "Il messaggio è obbligatorio.")
        @Size(max = 2000, message = "Il messaggio non può superare 2000 caratteri.")
        String message,
        @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "Identificativo di conversazione non valido.")
        String conversationId
) {}
