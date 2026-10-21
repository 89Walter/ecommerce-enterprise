package com.ecommerce.assistant.exception;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gli errori dell'API di Anthropic non arrivano al cliente con i loro dettagli tecnici:
 * vengono registrati nei log e tradotti in messaggi comprensibili.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiNotConfiguredException.class)
    public ResponseEntity<ApiErrorResponse> handleNotConfigured(AiNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleConversationNotFound(ConversationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    // Dal più specifico al più generico: Spring sceglie il gestore più vicino al tipo dell'eccezione.
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitException exception) {
        log.warn("Limite di richieste Anthropic raggiunto: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiErrorResponse("L'assistente è molto richiesto in questo momento: riprova tra poco."));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidKey(UnauthorizedException exception) {
        log.error("Chiave API Anthropic rifiutata: controlla ANTHROPIC_API_KEY nel file .env.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("L'assistente non è configurato correttamente."));
    }

    @ExceptionHandler(AnthropicServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleServiceError(AnthropicServiceException exception) {
        log.error("Errore dall'API Anthropic (HTTP {}): {}", exception.statusCode(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("L'assistente non è disponibile in questo momento: riprova più tardi."));
    }

    @ExceptionHandler(AnthropicException.class)
    public ResponseEntity<ApiErrorResponse> handleConnectionError(AnthropicException exception) {
        log.error("Impossibile contattare l'API Anthropic: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("L'assistente non è raggiungibile in questo momento: riprova più tardi."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ValidationErrorResponse("Richiesta non valida.", errors));
    }
}
