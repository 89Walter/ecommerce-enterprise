CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    city VARCHAR(120) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    cancellation_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6),
    shipped_at DATETIME(6),
    delivered_at DATETIME(6),
    cancelled_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_orders_customer_created (customer_id, created_at),
    INDEX idx_orders_status (status)
);

CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_items_order (order_id),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);
