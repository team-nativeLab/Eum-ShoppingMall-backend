ALTER TABLE orders
    ADD COLUMN cancel_previous_status VARCHAR(30);

ALTER TABLE orders
    ADD COLUMN cancel_requested_at DATETIME(6);

ALTER TABLE orders
    ADD COLUMN cancel_idempotency_key VARCHAR(300);
