ALTER TABLE payment
    ADD COLUMN virtual_account_bank_code VARCHAR(10),
    ADD COLUMN virtual_account_number VARCHAR(20),
    ADD COLUMN virtual_account_customer_name VARCHAR(100),
    ADD COLUMN virtual_account_due_at DATETIME(6),
    ADD COLUMN webhook_secret_hash VARCHAR(64);
