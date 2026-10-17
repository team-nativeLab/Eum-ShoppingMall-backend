ALTER TABLE member
    ADD COLUMN access_token_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE refresh_token
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
