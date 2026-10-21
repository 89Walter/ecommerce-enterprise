package com.ecommerce.order.order;

import com.ecommerce.order.messaging.EventTopology;
import com.ecommerce.order.messaging.IntegrationEvent;
import com.ecommerce.order.messaging.OutboxWriter;
import com.ecommerce.order.messaging.event.OrderCancelledEvent;
import com.ecommerce.order.messaging.event.OrderConfirmedEvent;
import com.ecommerce.order.messaging.event.OrderLinePayload;
import com.ecommerce.order.messaging.event.OrderPlacedEvent;
import com.ecommerce.order.messaging.event.ShippingAddressPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Traduce i cambi di stato dell'ordine in eventi, scritti nell'outbox nella stessa transazione. */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    static final String CURRENCY = "EUR";

    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public void orderPlaced(PurchaseOrder order) {
        append(order, EventTopology.ORDER_PLACED, new OrderPlacedEvent(
                IntegrationEvent.newEventId(),
                Instant.now(clock),
                order.getId(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getCustomerName(),
                order.getSubtotal(),
                CURRENCY,
                lines(order)
        ));
    }

    public void orderConfirmed(PurchaseOrder order) {
        ShippingAddress address = order.getShippingAddress();
        append(order, EventTopology.ORDER_CONFIRMED, new OrderConfirmedEvent(
                IntegrationEvent.newEventId(),
                Instant.now(clock),
                order.getId(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getCustomerName(),
                new ShippingAddressPayload(
                        address.getRecipientName(),
                        address.getAddressLine(),
                        address.getCity(),
                        address.getPostalCode(),
                        address.getCountryCode()
                ),
                lines(order)
        ));
    }

    public void orderCancelled(PurchaseOrder order) {
        append(order, EventTopology.ORDER_CANCELLED, new OrderCancelledEvent(
                IntegrationEvent.newEventId(),
                Instant.now(clock),
                order.getId(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getCustomerName(),
                order.getCancellationReason()
        ));
    }

    private void append(PurchaseOrder order, String routingKey, IntegrationEvent event) {
        outboxWriter.append(String.valueOf(order.getId()), routingKey, event);
    }

    private static List<OrderLinePayload> lines(PurchaseOrder order) {
        return order.getItems().stream()
                .map(item -> new OrderLinePayload(
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity()
                ))
                .toList();
    }
}
