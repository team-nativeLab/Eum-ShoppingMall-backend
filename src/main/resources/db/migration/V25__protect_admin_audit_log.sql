-- #131: 앱 계정(shopping_app)은 감사 로그 보관 정리(#67) 때문에 admin_audit_log를 지울 수 있다.
-- 앱이 뚫려도 감사 로그를 고치거나 최근 기록을 지워 흔적을 없애지 못하게 DB가 막는다. 어떤 계정으로 접속해도 같다.
-- 앱은 보존 기간(AUDIT_LOG_RETENTION_DAYS, 365일 이상)이 지난 로그만 지운다. 앱과 DB의 시계 차이를 감안해 364일로 막는다.
-- 바이너리 로그가 켜진 MySQL에서 트리거를 만들려면 log_bin_trust_function_creators=1이 필요하다(RDS 파라미터 그룹).
DELIMITER $$

CREATE TRIGGER trg_admin_audit_log_no_update
BEFORE UPDATE ON admin_audit_log
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'admin_audit_log rows cannot be updated';
END$$

CREATE TRIGGER trg_admin_audit_log_keep_recent
BEFORE DELETE ON admin_audit_log
FOR EACH ROW
BEGIN
    IF OLD.created_at > NOW() - INTERVAL 364 DAY THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'admin_audit_log rows newer than 364 days cannot be deleted';
    END IF;
END$$

DELIMITER ;
