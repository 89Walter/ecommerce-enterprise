package com.ecommerce.payment.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Gateway finto per lo sviluppo: approva gli importi fino a una soglia configurabile e rifiuta
 * quelli superiori, così si possono provare sia il percorso felice sia quello di errore.
 */
@Slf4j
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final BigDecimal maxApprovedAmount;

    public SimulatedPaymentGateway(@Value("${payment.simulation.max-approved-amount:5000}") BigDecimal maxApprovedAmount) {
        this.maxApprovedAmount = maxApprovedAmount;
    }

    @Override
    public ChargeResult charge(Long orderId, BigDecimal amount, String currency) {
        if (amount.compareTo(maxApprovedAmount) > 0) {
            log.info("Pagamento simulato rifiutato per l'ordine {}: {} {} oltre la soglia.", orderId, amount, currency);
            return ChargeResult.declined("Importo superiore al limite di " + maxApprovedAmount + " " + currency + ".");
        }
        return ChargeResult.approved("SIM-" + UUID.randomUUID());
    }

    @Override
    public String refund(String transactionReference, BigDecimal amount, String currency) {
        return "SIM-REFUND-" + UUID.randomUUID();
    }
}
