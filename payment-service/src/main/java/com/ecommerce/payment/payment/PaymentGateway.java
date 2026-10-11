package com.ecommerce.payment.payment;

import java.math.BigDecimal;

/**
 * Contratto verso il fornitore di pagamenti (Stripe, PayPal, Nexi...).
 * Qui un'interfaccia ha senso: le implementazioni sono intercambiabili e quella simulata
 * permette di sviluppare e testare senza un fornitore reale.
 */
public interface PaymentGateway {

    ChargeResult charge(Long orderId, BigDecimal amount, String currency);

    /** @return il riferimento del rimborso presso il fornitore. */
    String refund(String transactionReference, BigDecimal amount, String currency);

    record ChargeResult(boolean approved, String transactionReference, String failureReason) {

        public static ChargeResult approved(String transactionReference) {
            return new ChargeResult(true, transactionReference, null);
        }

        public static ChargeResult declined(String failureReason) {
            return new ChargeResult(false, null, failureReason);
        }
    }
}
