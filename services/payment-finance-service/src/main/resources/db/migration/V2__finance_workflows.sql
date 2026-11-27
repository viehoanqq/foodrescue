ALTER TABLE payments ADD COLUMN customer_id BIGINT;
ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE payments ADD COLUMN payment_url TEXT;
ALTER TABLE payments ADD COLUMN expires_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 1;
CREATE UNIQUE INDEX uq_payment_request ON payments(customer_id, idempotency_key);
-- One live attempt per order; a new key is allowed only after a definitive FAILED IPN.
CREATE UNIQUE INDEX uq_payment_live_order ON payments(order_id) WHERE status IN ('PENDING','SUCCESS');
ALTER TABLE refunds ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE payouts ADD COLUMN idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX uq_payout_request ON payouts(created_by, idempotency_key);
CREATE TABLE finance_refund_requests (
 order_id BIGINT PRIMARY KEY, amount NUMERIC(12,0) NOT NULL, reason VARCHAR(30) NOT NULL,
 event_id UUID NOT NULL, correlation_id UUID NOT NULL, actor_id BIGINT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE outbox_events ADD COLUMN claim_token UUID;
ALTER TABLE outbox_events ADD COLUMN claimed_until TIMESTAMPTZ;
CREATE INDEX idx_finance_payment_order ON payments(order_id);
CREATE INDEX idx_finance_snapshot_store_date ON order_snapshots(store_id,completed_at);
