ALTER TABLE users
    MODIFY COLUMN password_hash VARCHAR(255) NULL,
    MODIFY COLUMN phone VARCHAR(20) NULL,
    ADD COLUMN google_sub VARCHAR(255) NULL,
    ADD CONSTRAINT uq_users_google_sub UNIQUE (google_sub),
    ADD CONSTRAINT chk_users_auth_method CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL);
