package com.ecommerce.payment.payment;

import com.ecommerce.payment.messaging.EventTopology;
import com.ecommerce.payment.messaging.event.OrderCancelledEvent;
import com.ecommerce.payment.messaging.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventListeners {

    private final PaymentProcessingService paymentProcessingService;

    @RabbitListener(queues = EventTopology.PAYMENT_ORDER_PLACED_QUEUE)
    public void onOrderPlaced(OrderPlacedEvent event) {
        paymentProcessingService.onOrderPlaced(event);
    }

    @RabbitListener(queues = EventTopology.PAYMENT_ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        paymentProcessingService.onOrderCancelled(event);
    }
}
