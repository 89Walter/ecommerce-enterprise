package com.ecommerce.notification.messaging;

import java.time.Instant;
import java.util.UUID;

/** Campi comuni a tutti gli eventi scambiati tra microservizi. */
public interface IntegrationEvent {

    /** Identificativo univoco, usato da chi riceve l'evento per scartare i duplicati. */
    String eventId();

    Instant occurredAt();

    static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
