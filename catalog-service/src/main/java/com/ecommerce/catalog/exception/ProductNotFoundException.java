package com.ecommerce.catalog.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Prodotto con ID " + id + " non trovato.");
    }
}
