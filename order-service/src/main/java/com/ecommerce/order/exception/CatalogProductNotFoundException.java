package com.ecommerce.order.exception;

public class CatalogProductNotFoundException extends RuntimeException {

    public CatalogProductNotFoundException(Long productId) {
        super("Prodotto con ID " + productId + " non trovato nel Catalog.");
    }
}
