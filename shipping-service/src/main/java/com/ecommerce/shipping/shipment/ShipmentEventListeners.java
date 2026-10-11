package com.ecommerce.shipping.shipment;

import com.ecommerce.shipping.messaging.EventTopology;
import com.ecommerce.shipping.messaging.event.OrderCancelledEvent;
import com.ecommerce.shipping.messaging.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentEventListeners {

    private final ShipmentService shipmentService;

    @RabbitListener(queues = EventTopology.SHIPPING_ORDER_CONFIRMED_QUEUE)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        shipmentService.onOrderConfirmed(event);
    }

    @RabbitListener(queues = EventTopology.SHIPPING_ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        shipmentService.onOrderCancelled(event);
    }
}
