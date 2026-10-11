package com.ecommerce.shipping.shipment;

import com.ecommerce.shipping.messaging.event.ShippingAddressPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Una spedizione per ordine. Stati: CREATED -> SHIPPED -> DELIVERED, oppure CREATED -> CANCELLED.
 * L'indirizzo è una copia di quello dell'ordine al momento della conferma.
 */
@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, length = 255)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    @Column(length = 64)
    private String carrier;

    @Column(name = "tracking_code", length = 64, unique = true)
    private String trackingCode;

    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 120)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Version
    private long version;

    static Shipment create(Long orderId, String customerId, ShippingAddressPayload address,
                           String carrier, String trackingCode) {
        Shipment shipment = new Shipment();
        shipment.orderId = orderId;
        shipment.customerId = customerId;
        shipment.status = ShipmentStatus.CREATED;
        shipment.carrier = carrier;
        shipment.trackingCode = trackingCode;
        shipment.recipientName = address.recipientName();
        shipment.addressLine = address.addressLine();
        shipment.city = address.city();
        shipment.postalCode = address.postalCode();
        shipment.countryCode = address.countryCode();
        return shipment;
    }

    /** Segnaposto per un ordine annullato prima della conferma: impedisce di creare la spedizione dopo. */
    static Shipment cancelledBeforeCreation(Long orderId, String customerId, LocalDateTime cancelledAt) {
        Shipment shipment = new Shipment();
        shipment.orderId = orderId;
        shipment.customerId = customerId;
        shipment.status = ShipmentStatus.CANCELLED;
        shipment.cancelledAt = cancelledAt;
        return shipment;
    }

    void advanceTo(ShipmentStatus target, LocalDateTime changedAt) {
        boolean allowed = switch (status) {
            case CREATED -> target == ShipmentStatus.SHIPPED;
            case SHIPPED -> target == ShipmentStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new InvalidShipmentStateException(status, target);
        }
        status = target;
        if (target == ShipmentStatus.SHIPPED) {
            shippedAt = changedAt;
        } else {
            deliveredAt = changedAt;
        }
    }

    void cancel(LocalDateTime cancelledAt) {
        if (status != ShipmentStatus.CREATED) {
            throw new InvalidShipmentStateException(status, ShipmentStatus.CANCELLED);
        }
        status = ShipmentStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }
}
