ALTER TABLE orders
    ADD COLUMN cancel_reason VARCHAR(200) NULL,
    ADD COLUMN cancelled_by_member_id BIGINT NULL,
    ADD COLUMN cancelled_at DATETIME(6) NULL;
