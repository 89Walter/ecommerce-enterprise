package com.ecommerce.payment.payment;

import com.ecommerce.payment.messaging.EventTopology;
import com.ecommerce.payment.messaging.IdempotentConsumer;
import com.ecommerce.payment.messaging.IntegrationEvent;
import com.ecommerce.payment.messaging.OutboxWriter;
import com.ecommerce.payment.messaging.event.OrderCancelledEvent;
import com.ecommerce.payment.messaging.event.OrderPlacedEvent;
import com.ecommerce.payment.messaging.event.PaymentCompletedEvent;
import com.ecommerce.payment.messaging.event.PaymentFailedEvent;
import com.ecommerce.payment.messaging.event.PaymentRefundedEvent;
import com.ecommerce.payment.payment.PaymentGateway.ChargeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reagisce agli eventi degli ordini:
 * <ul>
 *     <li>ORDER_PLACED: addebito, poi PAYMENT_COMPLETED oppure PAYMENT_FAILED;</li>
 *     <li>ORDER_CANCELLED: rimborso se già addebitato, poi PAYMENT_REFUNDED.</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final OutboxWriter outboxWriter;
    private final IdempotentConsumer idempotentConsumer;
    private final Clock clock;

    public void onOrderPlaced(OrderPlacedEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        Optional<Payment> existing = paymentRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.info("Pagamento per l'ordine {} già presente in stato {}: nessun nuovo addebito.",
                    event.orderId(), existing.get().getStatus());
            return;
        }

        ChargeResult result = paymentGateway.charge(event.orderId(), event.amount(), event.currency());
        if (result.approved()) {
            Payment payment = paymentRepository.save(Payment.completed(
                    event.orderId(), event.customerId(), event.amount(), event.currency(),
                    result.transactionReference(), LocalDateTime.now(clock)
            ));
            publish(payment, EventTopology.PAYMENT_COMPLETED, new PaymentCompletedEvent(
                    IntegrationEvent.newEventId(), Instant.now(clock), payment.getOrderId(), payment.getId(),
                    payment.getAmount(), payment.getCurrency(), payment.getTransactionReference()
            ));
            log.info("Ordine {} addebitato con il pagamento {}.", payment.getOrderId(), payment.getId());
        } else {
            Payment payment = paymentRepository.save(Payment.failed(
                    event.orderId(), event.customerId(), event.amount(), event.currency(), result.failureReason()
            ));
            publish(payment, EventTopology.PAYMENT_FAILED, new PaymentFailedEvent(
                    IntegrationEvent.newEventId(), Instant.now(clock), payment.getOrderId(), payment.getId(),
                    payment.getFailureReason()
            ));
            log.info("Pagamento dell'ordine {} rifiutato: {}", payment.getOrderId(), payment.getFailureReason());
        }
    }

    public void onOrderCancelled(OrderCancelledEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        Optional<Payment> existing = paymentRepository.findByOrderId(event.orderId());
        if (existing.isEmpty()) {
            paymentRepository.save(Payment.cancelledBeforeCharge(event.orderId(), event.customerId()));
            log.info("Ordine {} annullato prima dell'addebito: il pagamento non verrà eseguito.", event.orderId());
            return;
        }

        Payment payment = existing.get();
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            return;
        }
        String refundReference = paymentGateway.refund(
                payment.getTransactionReference(), payment.getAmount(), payment.getCurrency()
        );
        payment.refund(refundReference, LocalDateTime.now(clock));
        paymentRepository.save(payment);
        publish(payment, EventTopology.PAYMENT_REFUNDED, new PaymentRefundedEvent(
                IntegrationEvent.newEventId(), Instant.now(clock), payment.getOrderId(), payment.getId(),
                payment.getAmount(), payment.getCurrency(), refundReference
        ));
        log.info("Pagamento {} dell'ordine {} rimborsato.", payment.getId(), payment.getOrderId());
    }

    private void publish(Payment payment, String routingKey, IntegrationEvent event) {
        outboxWriter.append(String.valueOf(payment.getOrderId()), routingKey, event);
    }
}
