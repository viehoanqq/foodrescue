ALTER TABLE categories
    ADD COLUMN archived_at TIMESTAMPTZ;

ALTER TABLE rescue_batches
    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE categories
    DROP CONSTRAINT uq_category_store_name;

CREATE UNIQUE INDEX uq_category_store_active_name
    ON categories (store_id, lower(name))
    WHERE archived_at IS NULL;

CREATE INDEX idx_categories_store_active_sort
    ON categories (store_id, sort_order, id)
    WHERE archived_at IS NULL;

CREATE INDEX idx_batch_store_status_deleted
    ON rescue_batches (store_id, status, deleted_at);
