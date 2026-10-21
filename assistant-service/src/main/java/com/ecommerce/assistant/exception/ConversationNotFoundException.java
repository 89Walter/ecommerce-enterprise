package com.ecommerce.assistant.exception;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversazione non trovata o scaduta: iniziane una nuova.");
    }
}
