package com.ecommerce.assistant.exception;

public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException() {
        super("L'assistente AI non è configurato: manca ANTHROPIC_API_KEY nel file .env.");
    }
}
