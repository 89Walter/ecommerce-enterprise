package com.ecommerce.notification.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A chi scrivere per un ordine. Si salva dagli eventi dell'order-service (che contengono email e nome)
 * e serve per quelli di pagamento e spedizione, che hanno solo il numero d'ordine.
 */
@Entity
@Table(name = "order_contacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderContact {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "customer_id", nullable = false, length = 255)
    private String customerId;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String name;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    OrderContact(Long orderId, String customerId, String email, String name, LocalDateTime updatedAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        update(email, name, updatedAt);
    }

    void update(String email, String name, LocalDateTime updatedAt) {
        this.email = email;
        this.name = name;
        this.updatedAt = updatedAt;
    }

    boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
