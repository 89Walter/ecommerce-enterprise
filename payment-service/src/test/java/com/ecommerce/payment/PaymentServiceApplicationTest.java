package com.ecommerce.payment;

import com.ecommerce.payment.messaging.EventTopology;
import com.ecommerce.payment.messaging.OutboxEvent;
import com.ecommerce.payment.messaging.OutboxEventRepository;
import com.ecommerce.payment.messaging.event.OrderCancelledEvent;
import com.ecommerce.payment.messaging.event.OrderPlacedEvent;
import com.ecommerce.payment.payment.Payment;
import com.ecommerce.payment.payment.PaymentProcessingService;
import com.ecommerce.payment.payment.PaymentRepository;
import com.ecommerce.payment.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Prova il flusso dei pagamenti con un database H2 reale (RabbitMQ non serve: gli eventi restano nell'outbox). */
@SpringBootTest
class PaymentServiceApplicationTest {

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void chargesOrderBelowSimulationLimitAndPublishesPaymentCompleted() {
        paymentProcessingService.onOrderPlaced(orderPlaced(101L, "250.00"));

        assertThat(payment(101L).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment(101L).getTransactionReference()).startsWith("SIM-");
        assertThat(routingKeys(101L)).containsExactly(EventTopology.PAYMENT_COMPLETED);
    }

    @Test
    void declinesOrderAboveSimulationLimitAndPublishesPaymentFailed() {
        paymentProcessingService.onOrderPlaced(orderPlaced(102L, "1500.00"));

        assertThat(payment(102L).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment(102L).getFailureReason()).contains("limite");
        assertThat(routingKeys(102L)).containsExactly(EventTopology.PAYMENT_FAILED);
    }

    @Test
    void ignoresDuplicateDeliveriesOfTheSameEvent() {
        OrderPlacedEvent event = orderPlaced(103L, "10.00");

        paymentProcessingService.onOrderPlaced(event);
        paymentProcessingService.onOrderPlaced(event);

        assertThat(routingKeys(103L)).containsExactly(EventTopology.PAYMENT_COMPLETED);
    }

    @Test
    void refundsCompletedPaymentWhenOrderIsCancelled() {
        paymentProcessingService.onOrderPlaced(orderPlaced(104L, "99.90"));

        paymentProcessingService.onOrderCancelled(orderCancelled(104L));

        assertThat(payment(104L).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment(104L).getRefundReference()).startsWith("SIM-REFUND-");
        assertThat(routingKeys(104L))
                .containsExactly(EventTopology.PAYMENT_COMPLETED, EventTopology.PAYMENT_REFUNDED);
    }

    @Test
    void neverChargesAnOrderCancelledBeforeThePaymentStarted() {
        paymentProcessingService.onOrderCancelled(orderCancelled(105L));
        paymentProcessingService.onOrderPlaced(orderPlaced(105L, "20.00"));

        assertThat(payment(105L).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(routingKeys(105L)).isEmpty();
    }

    private Payment payment(Long orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow();
    }

    private List<String> routingKeys(Long orderId) {
        return outboxEventRepository.findByAggregateIdOrderByIdAsc(String.valueOf(orderId)).stream()
                .map(OutboxEvent::getRoutingKey)
                .toList();
    }

    private static OrderPlacedEvent orderPlaced(Long orderId, String amount) {
        return new OrderPlacedEvent(UUID.randomUUID().toString(), Instant.now(), orderId, "customer-1",
                new BigDecimal(amount), "EUR");
    }

    private static OrderCancelledEvent orderCancelled(Long orderId) {
        return new OrderCancelledEvent(UUID.randomUUID().toString(), Instant.now(), orderId, "customer-1",
                "Changed my mind");
    }
}
