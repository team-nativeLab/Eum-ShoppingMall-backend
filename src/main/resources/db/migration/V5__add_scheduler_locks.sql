CREATE TABLE scheduler_lock (
    lock_name VARCHAR(100) NOT NULL,
    lock_until DATETIME(6) NOT NULL,
    lock_owner VARCHAR(36),
    last_order_id BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_scheduler_lock PRIMARY KEY (lock_name)
);

INSERT INTO scheduler_lock (lock_name, lock_until, lock_owner, last_order_id)
VALUES
    ('reconcileStuckPayments', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND), NULL, 0),
    ('reconcileStuckCancellations', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND), NULL, 0),
    ('purgeExpiredRefreshTokens', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND), NULL, 0),
    ('reconcileWaitingDeposits', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND), NULL, 0);
