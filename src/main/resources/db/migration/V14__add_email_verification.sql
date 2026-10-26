CREATE TABLE email_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    attempts INT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    token_hash VARCHAR(64),
    token_expires_at DATETIME(6),
    consumed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_email_verification PRIMARY KEY (id),
    INDEX ix_email_verification_email_purpose (email, purpose),
    INDEX ix_email_verification_token_hash (token_hash),
    INDEX ix_email_verification_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE member ADD COLUMN email_verified_at DATETIME(6) NULL;

INSERT INTO scheduler_lock (lock_name, lock_until, lock_owner, last_order_id)
VALUES ('purgeEmailVerifications', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND), NULL, 0);
