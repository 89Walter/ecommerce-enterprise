package com.ecommerce.shipping.shipment;

import com.ecommerce.shipping.messaging.EventTopology;
import com.ecommerce.shipping.messaging.IdempotentConsumer;
import com.ecommerce.shipping.messaging.IntegrationEvent;
import com.ecommerce.shipping.messaging.OutboxWriter;
import com.ecommerce.shipping.messaging.event.OrderCancelledEvent;
import com.ecommerce.shipping.messaging.event.OrderConfirmedEvent;
import com.ecommerce.shipping.messaging.event.ShippingUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * ORDER_CONFIRMED crea la spedizione; l'ADMIN la fa avanzare (SHIPPED, DELIVERED) e ogni cambio
 * di stato viene pubblicato come SHIPPING_UPDATED, che l'order-service usa per aggiornare l'ordine.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OutboxWriter outboxWriter;
    private final IdempotentConsumer idempotentConsumer;
    private final Clock clock;

    @Value("${shipping.default-carrier:SIMULATED}")
    private String defaultCarrier;

    public void onOrderConfirmed(OrderConfirmedEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        Optional<Shipment> existing = shipmentRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.info("Spedizione per l'ordine {} già presente in stato {}.", event.orderId(), existing.get().getStatus());
            return;
        }
        Shipment shipment = shipmentRepository.save(Shipment.create(
                event.orderId(), event.customerId(), event.shippingAddress(), defaultCarrier, newTrackingCode()
        ));
        publish(shipment);
        log.info("Creata la spedizione {} per l'ordine {}.", shipment.getId(), shipment.getOrderId());
    }

    public void onOrderCancelled(OrderCancelledEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        Optional<Shipment> existing = shipmentRepository.findByOrderId(event.orderId());
        if (existing.isEmpty()) {
            shipmentRepository.save(Shipment.cancelledBeforeCreation(event.orderId(), event.customerId(), now()));
            return;
        }
        Shipment shipment = existing.get();
        if (shipment.getStatus() != ShipmentStatus.CREATED) {
            log.warn("Ordine {} annullato ma la spedizione {} è già in stato {}: va gestita manualmente.",
                    event.orderId(), shipment.getId(), shipment.getStatus());
            return;
        }
        shipment.cancel(now());
        publish(shipmentRepository.save(shipment));
        log.info("Spedizione {} annullata insieme all'ordine {}.", shipment.getId(), shipment.getOrderId());
    }

    public ShipmentResponse advance(Long shipmentId, ShipmentStatus target) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException("Spedizione con ID " + shipmentId + " non trovata."));
        shipment.advanceTo(target, now());
        Shipment savedShipment = shipmentRepository.save(shipment);
        publish(savedShipment);
        return ShipmentResponse.from(savedShipment);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse findByOrder(Long orderId, String customerId, boolean admin) {
        return shipmentRepository.findByOrderId(orderId)
                .filter(shipment -> shipment.getTrackingCode() != null)
                .filter(shipment -> admin || shipment.getCustomerId().equals(customerId))
                .map(ShipmentResponse::from)
                .orElseThrow(() -> new ShipmentNotFoundException("Nessuna spedizione trovata per l'ordine " + orderId + "."));
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> findAll(ShipmentStatus status, Pageable pageable) {
        Page<Shipment> shipments = status == null
                ? shipmentRepository.findAll(pageable)
                : shipmentRepository.findByStatus(status, pageable);
        return shipments.map(ShipmentResponse::from);
    }

    private void publish(Shipment shipment) {
        outboxWriter.append(String.valueOf(shipment.getOrderId()), EventTopology.SHIPPING_UPDATED, new ShippingUpdatedEvent(
                IntegrationEvent.newEventId(),
                Instant.now(clock),
                shipment.getOrderId(),
                shipment.getId(),
                shipment.getStatus().name(),
                shipment.getCarrier(),
                shipment.getTrackingCode()
        ));
    }

    private static String newTrackingCode() {
        return "TRK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
