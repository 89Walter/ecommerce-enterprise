package com.ecommerce.order.order;

import com.ecommerce.order.messaging.EventTopology;
import com.ecommerce.order.messaging.event.PaymentCompletedEvent;
import com.ecommerce.order.messaging.event.PaymentFailedEvent;
import com.ecommerce.order.messaging.event.PaymentRefundedEvent;
import com.ecommerce.order.messaging.event.ShippingUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * API <b>asincrone</b> dell'order-service: non sono chiamate da un client HTTP, ma da RabbitMQ quando
 * un altro servizio pubblica un evento. Nessuno "aspetta" la risposta: il messaggio resta nella coda
 * finché il servizio non lo elabora (anche se in quel momento è spento), poi viene confermato (ack).
 * <p>
 * Se l'elaborazione lancia un'eccezione, Spring ritenta 3 volte (1s, 2s, 4s); poi il messaggio
 * finisce nella dead-letter queue {@code <coda>.dlq}. I duplicati sono scartati da {@code processed_events}.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListeners {

    private final OrderWorkflowService orderWorkflowService;

    /**
     * <b>ASINCRONA</b> — coda {@code order-service.payment-completed}, routing key {@code payment.completed},
     * pubblicato dal payment-service.
     * <p>
     * Ordine PLACED → CONFIRMED (solo se importo e valuta coincidono con il totale), poi pubblica
     * {@code order.confirmed}: lo shipping-service crea la spedizione, il notification-service manda l'email.
     */
    @RabbitListener(queues = EventTopology.ORDER_PAYMENT_COMPLETED_QUEUE)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        orderWorkflowService.onPaymentCompleted(event);
    }

    /**
     * <b>ASINCRONA</b> — coda {@code order-service.payment-failed}, routing key {@code payment.failed},
     * pubblicato dal payment-service.
     * <p>
     * Ordine PLACED → CANCELLED con il motivo del rifiuto, poi pubblica {@code order.cancelled}.
     */
    @RabbitListener(queues = EventTopology.ORDER_PAYMENT_FAILED_QUEUE)
    public void onPaymentFailed(PaymentFailedEvent event) {
        orderWorkflowService.onPaymentFailed(event);
    }

    /**
     * <b>ASINCRONA</b> — coda {@code order-service.payment-refunded}, routing key {@code payment.refunded},
     * pubblicato dal payment-service dopo l'annullamento di un ordine già pagato.
     * <p>
     * Registra la data del rimborso sull'ordine (campo {@code refundedAt}). Non pubblica altri eventi.
     */
    @RabbitListener(queues = EventTopology.ORDER_PAYMENT_REFUNDED_QUEUE)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        orderWorkflowService.onPaymentRefunded(event);
    }

    /**
     * <b>ASINCRONA</b> — coda {@code order-service.shipping-updated}, routing key {@code shipping.updated},
     * pubblicato dallo shipping-service a ogni cambio di stato della spedizione.
     * <p>
     * SHIPPED → ordine SHIPPED; DELIVERED → ordine DELIVERED. Gli stati CREATED e CANCELLED vengono ignorati.
     */
    @RabbitListener(queues = EventTopology.ORDER_SHIPPING_UPDATED_QUEUE)
    public void onShippingUpdated(ShippingUpdatedEvent event) {
        orderWorkflowService.onShippingUpdated(event);
    }
}
