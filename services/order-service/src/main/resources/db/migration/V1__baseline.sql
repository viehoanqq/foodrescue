-- order baseline schema; derived from database/foodrescue_schema.sql for MySQL.

CREATE TABLE cart_items (
    customer_id  BIGINT   NOT NULL,
    batch_id     BIGINT   NOT NULL,
    quantity     INT      NOT NULL,
    added_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id, batch_id),
    CONSTRAINT chk_cart_qty     CHECK (quantity BETWEEN 1 AND 5)
);

CREATE TABLE orders (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code         VARCHAR(30)   NOT NULL UNIQUE,
    customer_id        BIGINT        NOT NULL,
    store_id           BIGINT        NOT NULL,
    item_count         INT           NOT NULL,
    total_amount       DECIMAL(12,0) NOT NULL,
    commission_rate    DECIMAL(5,2)  NOT NULL,
    commission_amount  DECIMAL(12,0) NOT NULL,
    store_earning      DECIMAL(12,0) NOT NULL,
    pickup_start       DATETIME      NOT NULL,
    pickup_end         DATETIME      NOT NULL,
    pickup_code        VARCHAR(10)   NOT NULL UNIQUE,
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status     VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
    cancel_reason      VARCHAR(255),
    cancelled_by       BIGINT,
    cancelled_at       DATETIME,
    handled_by         BIGINT,
    payment_expires_at DATETIME      NOT NULL,
    completed_at       DATETIME,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_items     CHECK (item_count > 0),
    CONSTRAINT chk_order_money     CHECK (total_amount > 0
                                          AND commission_amount = ROUND(total_amount * commission_rate / 100)
                                          AND store_earning = total_amount - commission_amount),
    CONSTRAINT chk_order_pickup    CHECK (pickup_end > pickup_start),
    CONSTRAINT chk_order_done_time CHECK ((status IN ('COMPLETED', 'NO_SHOW')) = (completed_at IS NOT NULL))
);

CREATE TABLE order_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT        NOT NULL,
    batch_id    BIGINT        NOT NULL,
    quantity    INT           NOT NULL,
    unit_price  DECIMAL(12,0) NOT NULL,
    subtotal    DECIMAL(12,0) NOT NULL,
    CONSTRAINT fk_items_order  FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT uq_items_batch  UNIQUE (order_id, batch_id),
    CONSTRAINT chk_items_qty   CHECK (quantity BETWEEN 1 AND 5),
    CONSTRAINT chk_items_money CHECK (subtotal = unit_price * quantity)
);

CREATE INDEX idx_orders_store    ON orders(store_id, status, created_at);
CREATE INDEX idx_orders_customer ON orders(customer_id, created_at);

CREATE TABLE outbox_events (
  event_id CHAR(36) PRIMARY KEY,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id VARCHAR(80) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  schema_version INT NOT NULL DEFAULT 1,
  payload JSON NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  published_at DATETIME(6) NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NULL,
  last_error TEXT NULL,
  INDEX idx_outbox_pending (published_at, next_attempt_at, occurred_at)
);

CREATE TABLE inbox_events (
  consumer_name VARCHAR(100) NOT NULL,
  event_id CHAR(36) NOT NULL,
  processed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (consumer_name, event_id)
);
