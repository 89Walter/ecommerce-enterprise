package com.ecommerce.notification.notification;

import com.ecommerce.notification.messaging.EventTopology;
import com.ecommerce.notification.messaging.event.OrderCancelledEvent;
import com.ecommerce.notification.messaging.event.OrderConfirmedEvent;
import com.ecommerce.notification.messaging.event.PaymentRefundedEvent;
import com.ecommerce.notification.messaging.event.ShippingUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Le uniche API del notification-service, tutte <b>asincrone</b>: il servizio non ha endpoint HTTP
 * (a parte l'health check), reagisce solo agli eventi su RabbitMQ e manda email via SMTP.
 * Retry 3 volte (1s, 2s, 4s), poi dead-letter queue; duplicati scartati da {@code processed_events}
 * e dal registro {@code sent_notifications}.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListeners {

    private final NotificationService notificationService;

    /**
     * <b>ASINCRONA</b> — coda {@code notification-service.order-confirmed}, routing key {@code order.confirmed},
     * pubblicato dall'order-service. Salva email e nome del cliente e manda "Ordine #N confermato".
     */
    @RabbitListener(queues = EventTopology.NOTIFICATION_ORDER_CONFIRMED_QUEUE)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        notificationService.onOrderConfirmed(event);
    }

    /**
     * <b>ASINCRONA</b> — coda {@code notification-service.order-cancelled}, routing key {@code order.cancelled},
     * pubblicato dall'order-service. Manda "Ordine #N annullato" con il motivo.
     */
    @RabbitListener(queues = EventTopology.NOTIFICATION_ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        notificationService.onOrderCancelled(event);
    }

    /**
     * <b>ASINCRONA</b> — coda {@code notification-service.payment-refunded}, routing key {@code payment.refunded},
     * pubblicato dal payment-service. Manda "Rimborso dell'ordine #N" (l'email si ricava dall'ordine).
     */
    @RabbitListener(queues = EventTopology.NOTIFICATION_PAYMENT_REFUNDED_QUEUE)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        notificationService.onPaymentRefunded(event);
    }

    /**
     * <b>ASINCRONA</b> — coda {@code notification-service.shipping-updated}, routing key {@code shipping.updated},
     * pubblicato dallo shipping-service. Manda "spedito" (SHIPPED) o "consegnato" (DELIVERED); gli altri stati no.
     */
    @RabbitListener(queues = EventTopology.NOTIFICATION_SHIPPING_UPDATED_QUEUE)
    public void onShippingUpdated(ShippingUpdatedEvent event) {
        notificationService.onShippingUpdated(event);
    }
}
