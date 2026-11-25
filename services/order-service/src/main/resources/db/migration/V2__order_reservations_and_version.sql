-- V2: Add reservation_id, aggregate_version, and idempotency_keys to order_db for MySQL

ALTER TABLE orders ADD COLUMN reservation_id VARCHAR(36) UNIQUE;
ALTER TABLE orders ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE idempotency_keys (
    `key`         VARCHAR(128) PRIMARY KEY,
    target_type   VARCHAR(50)  NOT NULL,
    target_id     VARCHAR(64)  NOT NULL,
    response_body TEXT,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_reservation ON orders(reservation_id);
