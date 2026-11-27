CREATE TABLE reservation_recovery (
    reservation_id CHAR(36) PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    order_id BIGINT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_recovery_due (status, next_attempt_at),
    CONSTRAINT chk_recovery_status CHECK (status IN ('PENDING', 'RECOVERING', 'ORDERED', 'RELEASED'))
);
