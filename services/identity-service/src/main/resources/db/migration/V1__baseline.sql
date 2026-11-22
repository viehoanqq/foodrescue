-- identity baseline schema; derived from database/foodrescue_schema.sql.

USE identity_db;

CREATE TABLE users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    role           VARCHAR(20)  NOT NULL,
    email          VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    store_id       BIGINT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_role  CHECK (role IN ('ADMIN', 'STORE_OWNER', 'STORE_STAFF', 'CUSTOMER')),
    CONSTRAINT chk_users_staff CHECK ((role = 'STORE_STAFF') = (store_id IS NOT NULL))
);

CREATE TABLE audit_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id     BIGINT,
    action       VARCHAR(40)  NOT NULL,
    entity_type  VARCHAR(20)  NOT NULL,
    entity_id    BIGINT       NOT NULL,
    amount       DECIMAL(14,0),
    detail       VARCHAR(500),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);

CREATE INDEX idx_audit_time   ON audit_logs(created_at);


DELIMITER $$
CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append only'$$
CREATE TRIGGER trg_audit_no_delete BEFORE DELETE ON audit_logs FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append only'$$
DELIMITER ;


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
