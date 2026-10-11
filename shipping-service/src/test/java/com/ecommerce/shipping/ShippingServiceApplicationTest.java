package com.ecommerce.shipping;

import com.ecommerce.shipping.messaging.EventTopology;
import com.ecommerce.shipping.messaging.OutboxEvent;
import com.ecommerce.shipping.messaging.OutboxEventRepository;
import com.ecommerce.shipping.messaging.event.OrderCancelledEvent;
import com.ecommerce.shipping.messaging.event.OrderConfirmedEvent;
import com.ecommerce.shipping.messaging.event.ShippingAddressPayload;
import com.ecommerce.shipping.shipment.InvalidShipmentStateException;
import com.ecommerce.shipping.shipment.ShipmentNotFoundException;
import com.ecommerce.shipping.shipment.ShipmentRepository;
import com.ecommerce.shipping.shipment.ShipmentService;
import com.ecommerce.shipping.shipment.ShipmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ShippingServiceApplicationTest {

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void createsShipmentForConfirmedOrderAndFollowsItUntilDelivery() {
        shipmentService.onOrderConfirmed(orderConfirmed(201L));
        Long shipmentId = shipmentRepository.findByOrderId(201L).orElseThrow().getId();

        shipmentService.advance(shipmentId, ShipmentStatus.SHIPPED);
        var delivered = shipmentService.advance(shipmentId, ShipmentStatus.DELIVERED);

        assertThat(delivered.status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(delivered.trackingCode()).startsWith("TRK-");
        assertThat(delivered.city()).isEqualTo("Torino");
        assertThat(statuses(201L)).containsExactly("CREATED", "SHIPPED", "DELIVERED");
    }

    @Test
    void rejectsSkippingTheShippedState() {
        shipmentService.onOrderConfirmed(orderConfirmed(202L));
        Long shipmentId = shipmentRepository.findByOrderId(202L).orElseThrow().getId();

        assertThatThrownBy(() -> shipmentService.advance(shipmentId, ShipmentStatus.DELIVERED))
                .isInstanceOf(InvalidShipmentStateException.class)
                .hasMessage("Transizione spedizione non consentita: CREATED -> DELIVERED.");
    }

    @Test
    void cancelsShipmentThatHasNotLeftTheWarehouse() {
        shipmentService.onOrderConfirmed(orderConfirmed(203L));

        shipmentService.onOrderCancelled(orderCancelled(203L));

        assertThat(shipmentRepository.findByOrderId(203L).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(statuses(203L)).containsExactly("CREATED", "CANCELLED");
    }

    @Test
    void neverCreatesShipmentForOrderCancelledBeforeConfirmation() {
        shipmentService.onOrderCancelled(orderCancelled(204L));
        shipmentService.onOrderConfirmed(orderConfirmed(204L));

        assertThat(shipmentRepository.findByOrderId(204L).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(statuses(204L)).isEmpty();
    }

    @Test
    void customersCannotSeeOtherCustomersShipments() {
        shipmentService.onOrderConfirmed(orderConfirmed(205L));

        assertThat(shipmentService.findByOrder(205L, "customer-1", false).orderId()).isEqualTo(205L);
        assertThat(shipmentService.findByOrder(205L, "someone-else", true).orderId()).isEqualTo(205L);
        assertThatThrownBy(() -> shipmentService.findByOrder(205L, "someone-else", false))
                .isInstanceOf(ShipmentNotFoundException.class);
    }

    private List<String> statuses(Long orderId) {
        return outboxEventRepository.findByAggregateIdOrderByIdAsc(String.valueOf(orderId)).stream()
                .peek(event -> assertThat(event.getRoutingKey()).isEqualTo(EventTopology.SHIPPING_UPDATED))
                .map(OutboxEvent::getPayload)
                .map(payload -> payload.replaceAll(".*\"status\":\"([A-Z]+)\".*", "$1"))
                .toList();
    }

    private static OrderConfirmedEvent orderConfirmed(Long orderId) {
        return new OrderConfirmedEvent(UUID.randomUUID().toString(), Instant.now(), orderId, "customer-1",
                new ShippingAddressPayload("Ada Lovelace", "Via Roma 1", "Torino", "10100", "IT"));
    }

    private static OrderCancelledEvent orderCancelled(Long orderId) {
        return new OrderCancelledEvent(UUID.randomUUID().toString(), Instant.now(), orderId, "customer-1",
                "Changed my mind");
    }
}
