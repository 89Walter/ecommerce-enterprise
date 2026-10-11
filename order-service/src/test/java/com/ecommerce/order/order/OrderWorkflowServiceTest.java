package com.ecommerce.order.order;

import com.ecommerce.order.messaging.IdempotentConsumer;
import com.ecommerce.order.messaging.event.PaymentCompletedEvent;
import com.ecommerce.order.messaging.event.PaymentFailedEvent;
import com.ecommerce.order.messaging.event.PaymentRefundedEvent;
import com.ecommerce.order.messaging.event.ShippingUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderWorkflowServiceTest {

    private static final Instant INSTANT = Instant.parse("2026-10-10T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);

    private OrderRepository orderRepository;
    private OrderEventPublisher orderEventPublisher;
    private IdempotentConsumer idempotentConsumer;
    private OrderWorkflowService workflow;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        idempotentConsumer = mock(IdempotentConsumer.class);
        given(idempotentConsumer.firstDelivery(any())).willReturn(true);
        given(orderRepository.save(any(PurchaseOrder.class))).willAnswer(invocation -> invocation.getArgument(0));
        workflow = new OrderWorkflowService(
                orderRepository,
                orderEventPublisher,
                idempotentConsumer,
                Clock.fixed(INSTANT, ZoneOffset.UTC)
        );
    }

    @Test
    void confirmsPlacedOrderWhenPaymentCompletesAndAsksForShipment() {
        PurchaseOrder order = givenOrder();

        workflow.onPaymentCompleted(paymentCompleted());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(NOW);
        verify(orderEventPublisher).orderConfirmed(order);
    }

    @Test
    void cancelsPlacedOrderWhenPaymentFails() {
        PurchaseOrder order = givenOrder();

        workflow.onPaymentFailed(new PaymentFailedEvent("evt-2", INSTANT, 12L, 5L, "Fondi insufficienti"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Pagamento non riuscito: Fondi insufficienti");
        verify(orderEventPublisher).orderCancelled(order);
    }

    @Test
    void ignoresPaymentForAnOrderAlreadyCancelledByTheCustomer() {
        PurchaseOrder order = givenOrder();
        order.cancel("Changed my mind", NOW);

        workflow.onPaymentCompleted(paymentCompleted());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void recordsRefundOnCancelledOrderOnlyOnce() {
        PurchaseOrder order = givenOrder();
        order.cancel("Changed my mind", NOW);

        workflow.onPaymentRefunded(paymentRefunded("evt-5"));
        workflow.onPaymentRefunded(paymentRefunded("evt-6"));

        assertThat(order.getRefundedAt()).isEqualTo(NOW);
    }

    @Test
    void followsShipmentUntilDelivery() {
        PurchaseOrder order = givenOrder();
        order.advanceTo(OrderStatus.CONFIRMED, NOW);

        workflow.onShippingUpdated(shippingUpdated("evt-3", "SHIPPED"));
        workflow.onShippingUpdated(shippingUpdated("evt-4", "DELIVERED"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getShippedAt()).isEqualTo(NOW);
        assertThat(order.getDeliveredAt()).isEqualTo(NOW);
    }

    @Test
    void skipsDuplicateEvents() {
        PurchaseOrder order = givenOrder();
        given(idempotentConsumer.firstDelivery(any())).willReturn(false);

        workflow.onPaymentCompleted(paymentCompleted());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void ignoresEventsForUnknownOrders() {
        given(orderRepository.findById(12L)).willReturn(Optional.empty());

        workflow.onPaymentCompleted(paymentCompleted());

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher);
    }

    private PurchaseOrder givenOrder() {
        PurchaseOrder order = OrderServiceTest.purchaseOrder("customer-1");
        given(orderRepository.findById(12L)).willReturn(Optional.of(order));
        return order;
    }

    private static PaymentCompletedEvent paymentCompleted() {
        return new PaymentCompletedEvent("evt-1", INSTANT, 12L, 5L, new BigDecimal("23.40"), "EUR", "SIM-1");
    }

    private static PaymentRefundedEvent paymentRefunded(String eventId) {
        return new PaymentRefundedEvent(eventId, INSTANT, 12L, 5L, new BigDecimal("23.40"), "EUR", "SIM-REFUND-1");
    }

    private static ShippingUpdatedEvent shippingUpdated(String eventId, String status) {
        return new ShippingUpdatedEvent(eventId, INSTANT, 12L, 3L, status, "SIMULATED", "TRK-1");
    }
}
