package com.ecommerce.order.order;

import com.ecommerce.order.messaging.EventTopology;
import com.ecommerce.order.messaging.event.PaymentCompletedEvent;
import com.ecommerce.order.messaging.event.PaymentFailedEvent;
import com.ecommerce.order.messaging.event.PaymentRefundedEvent;
import com.ecommerce.order.messaging.event.ShippingUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListeners {

    private final OrderWorkflowService orderWorkflowService;

    @RabbitListener(queues = EventTopology.ORDER_PAYMENT_COMPLETED_QUEUE)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        orderWorkflowService.onPaymentCompleted(event);
    }

    @RabbitListener(queues = EventTopology.ORDER_PAYMENT_FAILED_QUEUE)
    public void onPaymentFailed(PaymentFailedEvent event) {
        orderWorkflowService.onPaymentFailed(event);
    }

    @RabbitListener(queues = EventTopology.ORDER_PAYMENT_REFUNDED_QUEUE)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        orderWorkflowService.onPaymentRefunded(event);
    }

    @RabbitListener(queues = EventTopology.ORDER_SHIPPING_UPDATED_QUEUE)
    public void onShippingUpdated(ShippingUpdatedEvent event) {
        orderWorkflowService.onShippingUpdated(event);
    }
}
