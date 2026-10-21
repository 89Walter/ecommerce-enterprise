package com.ecommerce.notification.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro delle email inviate. Il vincolo UNIQUE (order_id, type) impedisce di mandare due volte
 * la stessa comunicazione per lo stesso ordine, anche se l'evento arrivasse più volte.
 */
@Entity
@Table(name = "sent_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    SentNotification(Long orderId, NotificationType type, String recipient, String subject, LocalDateTime sentAt) {
        this.orderId = orderId;
        this.type = type;
        this.recipient = recipient;
        this.subject = subject;
        this.sentAt = sentAt;
    }
}
