CREATE TABLE email_otps (
    otp_type VARCHAR(32) NOT NULL,
    email VARCHAR(255) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    resend_after DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (otp_type, email)
);

INSERT INTO email_otps (otp_type, email, code_hash, expires_at, resend_after, attempts, created_at)
SELECT 'REGISTRATION', email, code_hash, expires_at, resend_after, attempts, created_at
FROM registration_email_otps;

DROP TABLE registration_email_otps;
