package com.ecommerce.order.exception;

import com.ecommerce.order.order.InvalidOrderStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleOrderNotFound(OrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(CatalogProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCatalogProductNotFound(CatalogProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidOrder(InvalidOrderException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidOrderState(InvalidOrderStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentOrderUpdate(
            ObjectOptimisticLockingFailureException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("L'ordine è stato modificato da un'altra richiesta. Ricarica i dati e riprova."));
    }

    @ExceptionHandler(CatalogServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleCatalogServiceFailure(CatalogServiceException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("Richiesta non valida.", errors));
    }
}
