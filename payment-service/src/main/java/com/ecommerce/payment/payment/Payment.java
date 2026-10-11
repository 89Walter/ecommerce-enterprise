package com.ecommerce.payment.payment;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un solo pagamento per ordine (vincolo UNIQUE su order_id). */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, length = 255)
    private String customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "transaction_reference", length = 64)
    private String transactionReference;

    @Column(name = "refund_reference", length = 64)
    private String refundReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Version
    private long version;

    private Payment(Long orderId, String customerId, BigDecimal amount, String currency, PaymentStatus status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
    }

    static Payment completed(Long orderId, String customerId, BigDecimal amount, String currency,
                             String transactionReference, LocalDateTime completedAt) {
        Payment payment = new Payment(orderId, customerId, amount, currency, PaymentStatus.COMPLETED);
        payment.transactionReference = transactionReference;
        payment.completedAt = completedAt;
        return payment;
    }

    static Payment failed(Long orderId, String customerId, BigDecimal amount, String currency, String reason) {
        Payment payment = new Payment(orderId, customerId, amount, currency, PaymentStatus.FAILED);
        payment.failureReason = reason;
        return payment;
    }

    /** Segnaposto per un ordine annullato prima dell'arrivo di ORDER_PLACED: impedisce un addebito successivo. */
    static Payment cancelledBeforeCharge(Long orderId, String customerId) {
        return new Payment(orderId, customerId, BigDecimal.ZERO.setScale(2), "EUR", PaymentStatus.CANCELLED);
    }

    void refund(String refundReference, LocalDateTime refundedAt) {
        if (status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Solo un pagamento completato può essere rimborsato.");
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundReference = refundReference;
        this.refundedAt = refundedAt;
    }
}
