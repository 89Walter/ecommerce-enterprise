package com.ecommerce.order.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 255)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Embedded
    private ShippingAddress shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Version
    private long version;

    private PurchaseOrder(
            String customerId,
            BigDecimal subtotal,
            ShippingAddress shippingAddress,
            List<OrderLineSnapshot> lines
    ) {
        this.customerId = customerId;
        this.status = OrderStatus.PLACED;
        this.subtotal = subtotal;
        this.shippingAddress = shippingAddress;
        lines.forEach(line -> items.add(OrderItem.create(
                this,
                line.productId(),
                line.productName(),
                line.unitPrice(),
                line.quantity()
        )));
    }

    static PurchaseOrder create(
            String customerId,
            BigDecimal subtotal,
            ShippingAddress shippingAddress,
            List<OrderLineSnapshot> lines
    ) {
        return new PurchaseOrder(customerId, subtotal, shippingAddress, lines);
    }

    public void advanceTo(OrderStatus target, LocalDateTime changedAt) {
        boolean allowed = switch (status) {
            case PLACED -> target == OrderStatus.CONFIRMED;
            case CONFIRMED -> target == OrderStatus.SHIPPED;
            case SHIPPED -> target == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new InvalidOrderStateException(status, target);
        }

        status = target;
        switch (target) {
            case CONFIRMED -> confirmedAt = changedAt;
            case SHIPPED -> shippedAt = changedAt;
            case DELIVERED -> deliveredAt = changedAt;
            default -> throw new IllegalStateException("Stato di avanzamento non gestito: " + target);
        }
    }

    public void cancel(String reason, LocalDateTime cancelledAt) {
        if (status != OrderStatus.PLACED && status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(status, OrderStatus.CANCELLED);
        }
        status = OrderStatus.CANCELLED;
        cancellationReason = reason;
        this.cancelledAt = cancelledAt;
    }

    /** Registra il rimborso del pagamento di un ordine annullato; i duplicati non cambiano la data. */
    public void markRefunded(LocalDateTime refundedAt) {
        if (status != OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(status, OrderStatus.CANCELLED);
        }
        if (this.refundedAt == null) {
            this.refundedAt = refundedAt;
        }
    }
}
