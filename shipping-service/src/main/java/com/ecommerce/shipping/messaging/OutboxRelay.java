package com.ecommerce.shipping.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Pubblica su RabbitMQ gli eventi salvati nella tabella outbox.
 * Un evento è segnato come pubblicato solo dopo la conferma del broker (publisher confirm)
 * e solo se almeno una coda lo ha ricevuto; altrimenti viene ritentato al giro successivo.
 * La consegna è quindi "almeno una volta": chi riceve scarta i duplicati tramite l'eventId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "messaging.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    @Value("${messaging.outbox.retention:7d}")
    private Duration retention;

    @Scheduled(fixedDelayString = "${messaging.outbox.poll-interval:1s}")
    public void publishPendingEvents() {
        transactionOperations.executeWithoutResult(status -> {
            List<OutboxEvent> batch = outboxEventRepository.lockNextBatch(PageRequest.of(0, BATCH_SIZE));
            for (OutboxEvent event : batch) {
                try {
                    publish(event);
                    event.markPublished(LocalDateTime.now(clock));
                } catch (Exception exception) {
                    event.markFailed(exception.getMessage());
                    log.warn("Pubblicazione dell'evento {} ({}) non riuscita, verrà ritentata: {}",
                            event.getEventId(), event.getRoutingKey(), exception.getMessage());
                    // Ci si ferma qui per non pubblicare gli eventi successivi prima di quello fallito.
                    break;
                }
            }
        });
    }

    @Scheduled(cron = "${messaging.outbox.cleanup-cron:0 0 3 * * *}")
    public void deleteOldPublishedEvents() {
        Integer deleted = transactionOperations.execute(status ->
                outboxEventRepository.deletePublishedBefore(LocalDateTime.now(clock).minus(retention)));
        if (deleted != null && deleted > 0) {
            log.info("Eliminati {} eventi outbox già pubblicati.", deleted);
        }
    }

    private void publish(OutboxEvent event) throws Exception {
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getEventId())
                .setHeader("eventType", event.getRoutingKey())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(event.getEventId());
        rabbitTemplate.send(EventTopology.EVENTS_EXCHANGE, event.getRoutingKey(), message, correlation);

        CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!confirm.ack()) {
            throw new IllegalStateException("Il broker ha rifiutato il messaggio: " + confirm.reason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("Nessuna coda collegata alla routing key " + event.getRoutingKey() + ".");
        }
    }
}
