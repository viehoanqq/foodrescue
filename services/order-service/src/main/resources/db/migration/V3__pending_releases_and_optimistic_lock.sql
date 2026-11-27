-- V3: Add refund_requested, idempotency_keys metadata, and pending_inventory_releases for resilient retry

ALTER TABLE orders ADD COLUMN refund_requested BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE idempotency_keys ADD COLUMN customer_id BIGINT;
ALTER TABLE idempotency_keys ADD COLUMN request_hash VARCHAR(64);

CREATE TABLE pending_inventory_releases (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id  VARCHAR(36) NOT NULL UNIQUE,
    reason          VARCHAR(255),
    attempts        INT         NOT NULL DEFAULT 0,
    next_attempt_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error      TEXT,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pending_releases_next ON pending_inventory_releases(next_attempt_at);
