CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);
CREATE INDEX ix_refresh_token_used_at ON refresh_token (used_at);
