-- A chi scrivere per ogni ordine (dagli eventi dell'order-service)
CREATE TABLE order_contacts (
    order_id BIGINT NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    name VARCHAR(255),
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (order_id)
);

-- Registro delle email inviate: al massimo una per tipo e per ordine
CREATE TABLE sent_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sent_notifications_order_type UNIQUE (order_id, type)
);

-- Idempotent Consumer: eventi ricevuti e già elaborati
CREATE TABLE processed_events (
    event_id VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
);
