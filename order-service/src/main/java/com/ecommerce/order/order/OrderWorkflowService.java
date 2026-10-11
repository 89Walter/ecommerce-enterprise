package com.ecommerce.order.order;

import com.ecommerce.order.messaging.IdempotentConsumer;
import com.ecommerce.order.messaging.event.PaymentCompletedEvent;
import com.ecommerce.order.messaging.event.PaymentFailedEvent;
import com.ecommerce.order.messaging.event.PaymentRefundedEvent;
import com.ecommerce.order.messaging.event.ShippingUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Fa avanzare gli ordini in base agli eventi degli altri servizi (coreografia):
 * <pre>
 * PLACED --pagamento riuscito--> CONFIRMED --spedito--> SHIPPED --consegnato--> DELIVERED
 *    \--pagamento fallito--> CANCELLED (+ data del rimborso, se il pagamento era già avvenuto)
 * </pre>
 * Gli eventi arrivati fuori ordine o dopo un annullamento vengono registrati e ignorati.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderWorkflowService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final IdempotentConsumer idempotentConsumer;
    private final Clock clock;

    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        findOrder(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.PLACED) {
                log.warn("Pagamento completato per l'ordine {} in stato {}: nessuna transizione.",
                        order.getId(), order.getStatus());
                return;
            }
            order.advanceTo(OrderStatus.CONFIRMED, now());
            orderEventPublisher.orderConfirmed(orderRepository.save(order));
            log.info("Ordine {} confermato dopo il pagamento {}.", order.getId(), event.paymentId());
        });
    }

    public void onPaymentFailed(PaymentFailedEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        findOrder(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.PLACED) {
                log.warn("Pagamento fallito per l'ordine {} in stato {}: nessuna transizione.",
                        order.getId(), order.getStatus());
                return;
            }
            order.cancel("Pagamento non riuscito: " + event.reason(), now());
            orderEventPublisher.orderCancelled(orderRepository.save(order));
            log.info("Ordine {} annullato per pagamento non riuscito.", order.getId());
        });
    }

    public void onPaymentRefunded(PaymentRefundedEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        findOrder(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.CANCELLED) {
                log.error("Rimborso ricevuto per l'ordine {} in stato {}: da verificare.", order.getId(), order.getStatus());
                return;
            }
            order.markRefunded(now());
            orderRepository.save(order);
            log.info("Ordine {} rimborsato ({}).", order.getId(), event.refundReference());
        });
    }

    public void onShippingUpdated(ShippingUpdatedEvent event) {
        if (!idempotentConsumer.firstDelivery(event)) {
            return;
        }
        OrderStatus target = switch (event.status()) {
            case "SHIPPED" -> OrderStatus.SHIPPED;
            case "DELIVERED" -> OrderStatus.DELIVERED;
            default -> null;
        };
        if (target == null) {
            return;
        }
        findOrder(event.orderId()).ifPresent(order -> {
            if (order.getStatus() == target) {
                return;
            }
            if (order.getStatus() == OrderStatus.CANCELLED) {
                log.warn("Aggiornamento spedizione {} ricevuto per l'ordine annullato {}: ignorato.",
                        event.status(), order.getId());
                return;
            }
            order.advanceTo(target, now());
            orderRepository.save(order);
            log.info("Ordine {} aggiornato a {} dalla spedizione {}.", order.getId(), target, event.shipmentId());
        });
    }

    private Optional<PurchaseOrder> findOrder(Long orderId) {
        Optional<PurchaseOrder> order = orderRepository.findById(orderId);
        if (order.isEmpty()) {
            log.error("Evento ricevuto per l'ordine {} che non esiste: ignorato.", orderId);
        }
        return order;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
