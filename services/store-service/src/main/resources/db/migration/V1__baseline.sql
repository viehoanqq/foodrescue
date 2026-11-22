-- store baseline schema; derived from database/foodrescue_schema.sql.

USE store_db;

CREATE TABLE stores (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id          BIGINT        NOT NULL UNIQUE,
    name              VARCHAR(150)  NOT NULL,
    description       TEXT,
    phone             VARCHAR(20)   NOT NULL,
    address           VARCHAR(255)  NOT NULL,
    latitude          DECIMAL(10,8) NOT NULL,
    longitude         DECIMAL(11,8) NOT NULL,
    image_url         VARCHAR(255),
    license_image_url VARCHAR(255)  NOT NULL,
    open_time         TIME          NOT NULL,
    close_time        TIME          NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reject_reason     VARCHAR(255),
    commission_rate   DECIMAL(5,2)  NOT NULL DEFAULT 15.00,
    bank_name         VARCHAR(100)  NOT NULL,
    bank_account_no   VARCHAR(30)   NOT NULL,
    bank_account_name VARCHAR(100)  NOT NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_store_time  CHECK (close_time > open_time),
    CONSTRAINT chk_store_rate  CHECK (commission_rate BETWEEN 0 AND 50)
);

CREATE TABLE reviews (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT   NOT NULL UNIQUE,
    customer_id  BIGINT   NOT NULL,
    store_id     BIGINT   NOT NULL,
    rating       TINYINT  NOT NULL,
    comment      TEXT,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_store    FOREIGN KEY (store_id)    REFERENCES stores(id),
    CONSTRAINT chk_review_rating   CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_stores_status_geo ON stores(status, latitude, longitude);


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
