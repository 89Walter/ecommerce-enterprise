package com.ecommerce.catalog.exception;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.ecommerce.catalog.ai.AiNotConfiguredException;
import com.ecommerce.catalog.ai.DescriptionNotGeneratedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotFound(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        ValidationErrorResponse response = new ValidationErrorResponse("Richiesta non valida.", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    public ResponseEntity<ApiErrorResponse> handleAiNotConfigured(AiNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(DescriptionNotGeneratedException.class)
    public ResponseEntity<ApiErrorResponse> handleDescriptionNotGenerated(DescriptionNotGeneratedException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    // Errori dell'API di Anthropic: dettagli nei log, messaggio semplice al client.
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleAiRateLimit(RateLimitException exception) {
        log.warn("Limite di richieste Anthropic raggiunto: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiErrorResponse("Troppe richieste al servizio AI: riprova tra poco."));
    }

    @ExceptionHandler(AnthropicServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleAiServiceError(AnthropicServiceException exception) {
        log.error("Errore dall'API Anthropic (HTTP {}): {}", exception.statusCode(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("Il servizio AI non è disponibile in questo momento: riprova più tardi."));
    }

    @ExceptionHandler(AnthropicException.class)
    public ResponseEntity<ApiErrorResponse> handleAiConnectionError(AnthropicException exception) {
        log.error("Impossibile contattare l'API Anthropic: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("Il servizio AI non è raggiungibile in questo momento: riprova più tardi."));
    }
}
