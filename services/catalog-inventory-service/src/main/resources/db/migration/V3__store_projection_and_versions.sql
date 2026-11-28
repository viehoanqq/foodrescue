-- Store projection: Catalog nhận store.status-changed.v1 để biết cửa hàng nào đã APPROVED
-- và ai là chủ (users.store_id của STORE_OWNER luôn NULL nên không lấy được từ JWT).
-- store_id/owner_id là tham chiếu scalar, không có FK xuyên DB.
CREATE TABLE store_status_projection (
    store_id          BIGINT       PRIMARY KEY,
    owner_id          BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    aggregate_version BIGINT       NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_projection_owner UNIQUE (owner_id),
    CONSTRAINT chk_store_projection_status CHECK (status IN ('APPROVED', 'REJECTED', 'LOCKED')),
    CONSTRAINT chk_store_projection_version CHECK (aggregate_version > 0)
);

-- Version tăng mỗi lần đổi, dùng làm aggregateVersion của event (bắt đầu từ 1).
ALTER TABLE categories     ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE rescue_batches ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 1;
