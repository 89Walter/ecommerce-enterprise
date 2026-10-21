package com.ecommerce.payment.payment;

import com.ecommerce.payment.messaging.EventTopology;
import com.ecommerce.payment.messaging.event.OrderCancelledEvent;
import com.ecommerce.payment.messaging.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * API <b>asincrone</b> del payment-service, invocate da RabbitMQ e non da un client HTTP.
 * Retry 3 volte (1s, 2s, 4s), poi dead-letter queue; duplicati scartati da {@code processed_events}.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListeners {

    private final PaymentProcessingService paymentProcessingService;

    /**
     * <b>ASINCRONA</b> — coda {@code payment-service.order-placed}, routing key {@code order.placed},
     * pubblicato dall'order-service alla creazione dell'ordine.
     * <p>
     * Esegue l'addebito con il gateway di pagamento (simulato: rifiuta sopra 5000 €) e pubblica
     * {@code payment.completed} oppure {@code payment.failed}. Se l'ordine era già stato annullato, non addebita.
     */
    @RabbitListener(queues = EventTopology.PAYMENT_ORDER_PLACED_QUEUE)
    public void onOrderPlaced(OrderPlacedEvent event) {
        paymentProcessingService.onOrderPlaced(event);
    }

    /**
     * <b>ASINCRONA</b> — coda {@code payment-service.order-cancelled}, routing key {@code order.cancelled},
     * pubblicato dall'order-service (annullamento del cliente o pagamento rifiutato).
     * <p>
     * Se il pagamento era COMPLETED lo rimborsa e pubblica {@code payment.refunded}; se l'addebito non era ancora
     * partito salva un segnaposto CANCELLED, così un {@code order.placed} arrivato dopo non addebita più.
     */
    @RabbitListener(queues = EventTopology.PAYMENT_ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        paymentProcessingService.onOrderCancelled(event);
    }
}
