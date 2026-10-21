package com.ecommerce.catalog.ai;

public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException() {
        super("La generazione delle descrizioni non è configurata: manca ANTHROPIC_API_KEY nel file .env.");
    }
}
