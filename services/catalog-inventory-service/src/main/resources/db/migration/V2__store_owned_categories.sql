-- Each category belongs to one store. A batch must reference a category of that same store.
-- Store IDs remain scalar cross-service references; there is no FK to store_db.

ALTER TABLE categories ADD COLUMN store_id BIGINT;
ALTER TABLE categories ADD COLUMN description VARCHAR(1000);

-- Remember the old category/store pairs before category IDs are reassigned.
CREATE TEMP TABLE old_category_store ON COMMIT DROP AS
SELECT DISTINCT category_id, store_id FROM rescue_batches;

UPDATE categories c SET store_id = (
    SELECT MIN(m.store_id) FROM old_category_store m WHERE m.category_id = c.id
);

-- Unused global categories have no owner to infer. Preserve them outside the active table.
CREATE TABLE legacy_unassigned_categories (
    original_id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    image_url VARCHAR(255),
    sort_order INT NOT NULL
);
INSERT INTO legacy_unassigned_categories (original_id, name, description, image_url, sort_order)
SELECT id, name, description, image_url, sort_order FROM categories WHERE store_id IS NULL;
DELETE FROM categories WHERE store_id IS NULL;

ALTER TABLE categories DROP CONSTRAINT categories_name_key;

-- The first store keeps the original ID; other stores get their own category row.
INSERT INTO categories (name, description, image_url, sort_order, store_id)
SELECT c.name, c.description, c.image_url, c.sort_order, m.store_id
FROM old_category_store m
JOIN categories c ON c.id = m.category_id
WHERE c.store_id <> m.store_id;

UPDATE rescue_batches b SET category_id = owned.id
FROM old_category_store m
JOIN categories original ON original.id = m.category_id
JOIN categories owned ON owned.name = original.name AND owned.store_id = m.store_id
WHERE b.category_id = m.category_id AND b.store_id = m.store_id;

ALTER TABLE categories ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE categories ADD CONSTRAINT uq_category_store_name UNIQUE (store_id, name);
ALTER TABLE categories ADD CONSTRAINT uq_category_id_store UNIQUE (id, store_id);
CREATE INDEX idx_categories_store_sort ON categories (store_id, sort_order, id);

ALTER TABLE rescue_batches DROP CONSTRAINT fk_batch_category;
ALTER TABLE rescue_batches ADD CONSTRAINT fk_batch_store_category
    FOREIGN KEY (category_id, store_id) REFERENCES categories (id, store_id);
