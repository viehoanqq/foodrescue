CREATE TABLE registration_email_otps (
    email VARCHAR(255) PRIMARY KEY,
    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    resend_after DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL
);
