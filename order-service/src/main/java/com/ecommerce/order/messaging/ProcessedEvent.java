package com.ecommerce.order.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/** Registro degli eventi ricevuti già elaborati (Idempotent Consumer). */
@Entity
@Table(name = "processed_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent implements Persistable<String> {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    ProcessedEvent(String eventId, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    @Override
    public String getId() {
        return eventId;
    }

    /** Sempre nuovo: un duplicato concorrente fallisce sulla chiave primaria invece di aggiornare la riga. */
    @Override
    public boolean isNew() {
        return true;
    }
}
