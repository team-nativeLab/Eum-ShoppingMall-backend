INSERT INTO scheduler_lock (lock_name, lock_until, lock_owner, last_order_id)
VALUES ('purgeUnusedImages', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND), NULL, 0);
