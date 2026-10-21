package com.ecommerce.notification.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SentNotificationRepository extends JpaRepository<SentNotification, Long> {

    boolean existsByOrderIdAndType(Long orderId, NotificationType type);

    List<SentNotification> findByOrderIdOrderByIdAsc(Long orderId);
}
