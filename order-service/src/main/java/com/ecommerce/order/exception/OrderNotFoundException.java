package com.ecommerce.order.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Ordine con ID " + id + " non trovato.");
    }
}
