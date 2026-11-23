CREATE TABLE store_status_projection (
  store_id BIGINT PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_store_status_projection CHECK (status IN ('APPROVED', 'REJECTED', 'LOCKED'))
);
