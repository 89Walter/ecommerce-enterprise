package com.ecommerce.shipping.shipment;

import com.ecommerce.shipping.messaging.EventTopology;
import com.ecommerce.shipping.messaging.event.OrderCancelledEvent;
import com.ecommerce.shipping.messaging.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * API <b>asincrone</b> dello shipping-service, invocate da RabbitMQ e non da un client HTTP.
 * Retry 3 volte (1s, 2s, 4s), poi dead-letter queue; duplicati scartati da {@code processed_events}.
 */
@Component
@RequiredArgsConstructor
public class ShipmentEventListeners {

    private final ShipmentService shipmentService;

    /**
     * <b>ASINCRONA</b> — coda {@code shipping-service.order-confirmed}, routing key {@code order.confirmed},
     * pubblicato dall'order-service dopo il pagamento.
     * <p>
     * Crea la spedizione in stato CREATED, con codice di tracking e copia dell'indirizzo, e pubblica
     * {@code shipping.updated}. Se l'ordine era già stato annullato non la crea.
     */
    @RabbitListener(queues = EventTopology.SHIPPING_ORDER_CONFIRMED_QUEUE)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        shipmentService.onOrderConfirmed(event);
    }

    /**
     * <b>ASINCRONA</b> — coda {@code shipping-service.order-cancelled}, routing key {@code order.cancelled},
     * pubblicato dall'order-service.
     * <p>
     * Annulla la spedizione se il pacco non è ancora partito (CREATED → CANCELLED) e pubblica
     * {@code shipping.updated}; se la spedizione non esiste ancora salva un segnaposto CANCELLED.
     */
    @RabbitListener(queues = EventTopology.SHIPPING_ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        shipmentService.onOrderCancelled(event);
    }
}
