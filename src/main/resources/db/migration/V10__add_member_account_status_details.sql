ALTER TABLE member
    ADD COLUMN withdrawn_at DATETIME(6) NULL,
    ADD COLUMN suspension_reason VARCHAR(200) NULL;
