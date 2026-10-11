-- Transactional Outbox: eventi da pubblicare su RabbitMQ
CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_events_event_id UNIQUE (event_id),
    INDEX idx_outbox_events_pending (published_at, id)
);

-- Idempotent Consumer: eventi ricevuti e già elaborati
CREATE TABLE processed_events (
    event_id VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
);
