package com.ecommerce.order.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * Contratto condiviso della messaggistica: exchange, routing key e code.
 * La classe è identica in tutti i microservizi e ognuno dichiara l'intera topologia all'avvio:
 * così un evento pubblicato trova sempre la coda di destinazione, anche se il servizio che lo
 * consuma non è ancora partito (i messaggi lo aspettano in coda).
 * La descrizione completa degli eventi è in docs/events.md.
 */
public final class EventTopology {

    public static final String EVENTS_EXCHANGE = "ecommerce.events";
    public static final String DEAD_LETTER_EXCHANGE = "ecommerce.events.dlx";

    public static final String ORDER_PLACED = "order.placed";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String SHIPPING_UPDATED = "shipping.updated";

    public static final String PAYMENT_ORDER_PLACED_QUEUE = "payment-service.order-placed";
    public static final String PAYMENT_ORDER_CANCELLED_QUEUE = "payment-service.order-cancelled";
    public static final String ORDER_PAYMENT_COMPLETED_QUEUE = "order-service.payment-completed";
    public static final String ORDER_PAYMENT_FAILED_QUEUE = "order-service.payment-failed";
    public static final String ORDER_PAYMENT_REFUNDED_QUEUE = "order-service.payment-refunded";
    public static final String ORDER_SHIPPING_UPDATED_QUEUE = "order-service.shipping-updated";
    public static final String SHIPPING_ORDER_CONFIRMED_QUEUE = "shipping-service.order-confirmed";
    public static final String SHIPPING_ORDER_CANCELLED_QUEUE = "shipping-service.order-cancelled";
    public static final String NOTIFICATION_ORDER_CONFIRMED_QUEUE = "notification-service.order-confirmed";
    public static final String NOTIFICATION_ORDER_CANCELLED_QUEUE = "notification-service.order-cancelled";
    public static final String NOTIFICATION_PAYMENT_REFUNDED_QUEUE = "notification-service.payment-refunded";
    public static final String NOTIFICATION_SHIPPING_UPDATED_QUEUE = "notification-service.shipping-updated";

    /** Chi riceve cosa: una coda per ogni coppia (servizio consumatore, tipo di evento). */
    public static final List<QueueBinding> BINDINGS = List.of(
            new QueueBinding(PAYMENT_ORDER_PLACED_QUEUE, ORDER_PLACED),
            new QueueBinding(PAYMENT_ORDER_CANCELLED_QUEUE, ORDER_CANCELLED),
            new QueueBinding(ORDER_PAYMENT_COMPLETED_QUEUE, PAYMENT_COMPLETED),
            new QueueBinding(ORDER_PAYMENT_FAILED_QUEUE, PAYMENT_FAILED),
            new QueueBinding(ORDER_PAYMENT_REFUNDED_QUEUE, PAYMENT_REFUNDED),
            new QueueBinding(ORDER_SHIPPING_UPDATED_QUEUE, SHIPPING_UPDATED),
            new QueueBinding(SHIPPING_ORDER_CONFIRMED_QUEUE, ORDER_CONFIRMED),
            new QueueBinding(SHIPPING_ORDER_CANCELLED_QUEUE, ORDER_CANCELLED),
            new QueueBinding(NOTIFICATION_ORDER_CONFIRMED_QUEUE, ORDER_CONFIRMED),
            new QueueBinding(NOTIFICATION_ORDER_CANCELLED_QUEUE, ORDER_CANCELLED),
            new QueueBinding(NOTIFICATION_PAYMENT_REFUNDED_QUEUE, PAYMENT_REFUNDED),
            new QueueBinding(NOTIFICATION_SHIPPING_UPDATED_QUEUE, SHIPPING_UPDATED)
    );

    private EventTopology() {}

    /**
     * Exchange, code durevoli e relative dead-letter queue: i messaggi che falliscono anche
     * dopo i retry finiscono in "coda.dlq", dove restano visibili per essere analizzati.
     */
    public static Declarables declarables() {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
        List<Declarable> declarables = new ArrayList<>(List.of(events, deadLetters));
        for (QueueBinding queueBinding : BINDINGS) {
            Queue queue = QueueBuilder.durable(queueBinding.queue())
                    .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(queueBinding.queue())
                    .build();
            Queue deadLetterQueue = QueueBuilder.durable(queueBinding.queue() + ".dlq").build();
            declarables.add(queue);
            declarables.add(deadLetterQueue);
            declarables.add(BindingBuilder.bind(queue).to(events).with(queueBinding.routingKey()));
            declarables.add(BindingBuilder.bind(deadLetterQueue).to(deadLetters).with(queueBinding.queue()));
        }
        return new Declarables(declarables);
    }

    public record QueueBinding(String queue, String routingKey) {}
}
