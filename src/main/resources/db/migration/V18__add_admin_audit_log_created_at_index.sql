-- 감사 로그를 조건 없이 또는 기간으로 최신순 조회할 때 표 전체를 읽고 정렬하지 않게 한다(#117).
CREATE INDEX idx_admin_audit_created ON admin_audit_log (created_at, id);
