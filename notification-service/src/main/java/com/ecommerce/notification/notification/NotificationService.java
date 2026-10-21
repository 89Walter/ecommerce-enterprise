package com.ecommerce.notification.notification;

import com.ecommerce.notification.messaging.IdempotentConsumer;
import com.ecommerce.notification.messaging.event.OrderCancelledEvent;
import com.ecommerce.notification.messaging.event.OrderConfirmedEvent;
import com.ecommerce.notification.messaging.event.PaymentRefundedEvent;
import com.ecommerce.notification.messaging.event.ShippingUpdatedEvent;
import com.ecommerce.notification.notification.EmailComposer.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Trasforma gli eventi in email al cliente:
 * <pre>
 * order.confirmed  → "Ordine confermato"      order.cancelled → "Ordine annullato"
 * payment.refunded → "Rimborso effettuato"    shipping.updated (SHIPPED / DELIVERED) → "Spedito" / "Consegnato"
 * </pre>
 * Ogni evento è elaborato in una transazione: se l'invio SMTP fallisce, la transazione viene annullata
 * e RabbitMQ ritenta il messaggio (poi la dead-letter queue). La consegna resta "almeno una volta":
 * in un caso raro (email partita ma commit fallito) il cliente può ricevere un doppione.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final IdempotentConsumer idempotentConsumer;
    private final OrderContactRepository contactRepository;
    private final SentNotificationRepository sentNotificationRepository;
    private final EmailComposer composer;
    private final EmailSender emailSender;
    private final Clock clock;

    public void onOrderConfirmed(OrderConfirmedEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        OrderContact contact = saveContact(event.orderId(), event.customerId(), event.customerEmail(), event.customerName());
        notify(contact, NotificationType.ORDER_CONFIRMED,
                composer.orderConfirmed(event.orderId(), contact.getName(), event.shippingAddress(), event.items()));
    }

    public void onOrderCancelled(OrderCancelledEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        OrderContact contact = saveContact(event.orderId(), event.customerId(), event.customerEmail(), event.customerName());
        notify(contact, NotificationType.ORDER_CANCELLED,
                composer.orderCancelled(event.orderId(), contact.getName(), event.reason()));
    }

    public void onPaymentRefunded(PaymentRefundedEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        OrderContact contact = knownContact(event.orderId());
        notify(contact, NotificationType.PAYMENT_REFUNDED,
                composer.paymentRefunded(event.orderId(), contact.getName(), event.amount()));
    }

    public void onShippingUpdated(ShippingUpdatedEvent event) {
        NotificationType type = switch (event.status()) {
            case "SHIPPED" -> NotificationType.ORDER_SHIPPED;
            case "DELIVERED" -> NotificationType.ORDER_DELIVERED;
            default -> null;
        };
        // CREATED e CANCELLED non generano email: il cliente le ha già ricevute come "confermato" e "annullato".
        if (type == null || !idempotentConsumer.firstDelivery(event)) {
            return;
        }
        OrderContact contact = knownContact(event.orderId());
        EmailMessage email = type == NotificationType.ORDER_SHIPPED
                ? composer.orderShipped(event.orderId(), contact.getName(), event.carrier(), event.trackingCode())
                : composer.orderDelivered(event.orderId(), contact.getName());
        notify(contact, type, email);
    }

    private OrderContact saveContact(Long orderId, String customerId, String email, String name) {
        LocalDateTime now = LocalDateTime.now(clock);
        OrderContact contact = contactRepository.findById(orderId)
                .map(existing -> {
                    existing.update(email, name, now);
                    return existing;
                })
                .orElseGet(() -> new OrderContact(orderId, customerId, email, name, now));
        return contactRepository.save(contact);
    }

    private OrderContact knownContact(Long orderId) {
        return contactRepository.findById(orderId).orElseThrow(() -> new ContactNotYetKnownException(orderId));
    }

    private void notify(OrderContact contact, NotificationType type, EmailMessage email) {
        if (!contact.hasEmail()) {
            // Ordini creati prima delle notifiche, o token senza email: niente da inviare.
            log.warn("Nessuna email per l'ordine {}: notifica {} non inviata.", contact.getOrderId(), type);
            return;
        }
        if (sentNotificationRepository.existsByOrderIdAndType(contact.getOrderId(), type)) {
            log.info("Notifica {} per l'ordine {} già inviata.", type, contact.getOrderId());
            return;
        }
        emailSender.send(contact.getEmail(), email);
        sentNotificationRepository.save(new SentNotification(
                contact.getOrderId(), type, contact.getEmail(), email.subject(), LocalDateTime.now(clock)));
        log.info("Inviata la notifica {} per l'ordine {}.", type, contact.getOrderId());
    }
}
