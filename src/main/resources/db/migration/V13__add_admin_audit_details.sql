ALTER TABLE admin_audit_log MODIFY actor_member_id BIGINT NULL;
ALTER TABLE admin_audit_log ADD COLUMN details TEXT NULL AFTER summary;
