CREATE TABLE notification_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel VARCHAR(20) NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    variables TEXT NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(500),
    sent_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_notification_message PRIMARY KEY (id),
    INDEX ix_notification_status_next (status, next_attempt_at),
    INDEX ix_notification_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO scheduler_lock (lock_name, lock_until, lock_owner, last_order_id)
VALUES ('dispatchNotifications', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND), NULL, 0);
