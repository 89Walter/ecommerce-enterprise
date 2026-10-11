package com.ecommerce.payment.payment;

public enum PaymentStatus {
    /** Addebito riuscito. */
    COMPLETED,
    /** Addebito rifiutato dal gateway. */
    FAILED,
    /** Addebito rimborsato dopo l'annullamento dell'ordine. */
    REFUNDED,
    /** Ordine annullato prima che il pagamento partisse: nessun addebito verrà eseguito. */
    CANCELLED
}
