package com.ecommerce.payment.payment;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long orderId) {
        super("Nessun pagamento trovato per l'ordine " + orderId + ".");
    }
}
