CREATE TABLE admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_member_id BIGINT NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    action VARCHAR(10) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    summary VARCHAR(500) NOT NULL,
    response_status INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_admin_audit_log PRIMARY KEY (id),
    INDEX idx_admin_audit_actor_created (actor_member_id, created_at),
    INDEX idx_admin_audit_target_created (target_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
