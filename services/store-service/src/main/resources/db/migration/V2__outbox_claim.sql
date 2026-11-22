-- A short database lease lets multiple publisher instances claim different events
-- without holding a transaction open while waiting for RabbitMQ confirms.
ALTER TABLE outbox_events
    ADD COLUMN claim_token CHAR(36) NULL,
    ADD COLUMN claim_until DATETIME(6) NULL,
    ADD INDEX idx_outbox_claim (published_at, next_attempt_at, claim_until, occurred_at);
