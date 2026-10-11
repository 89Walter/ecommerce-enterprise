package com.ecommerce.order.order;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(OrderStatus current, OrderStatus target) {
        super("Transizione ordine non consentita: " + current + " -> " + target + ".");
    }
}
