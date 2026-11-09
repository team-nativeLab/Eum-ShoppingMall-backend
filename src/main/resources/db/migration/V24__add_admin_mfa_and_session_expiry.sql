-- #164: 관리자 OTP(TOTP) 2단계 인증과 관리자 세션 종료 시각

-- OTP 비밀 키는 서버가 코드를 계산해야 하므로 해시가 아니라 서버 키로 암호화해 둔다(SecretCipher).
-- 처음 맞는 코드를 넣으면 enabled_at이 채워진다. last_used_step은 한 번 받은 코드를 다시 받지 않으려고 둔다.
CREATE TABLE admin_mfa (
    member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    secret_ciphertext VARCHAR(255) NOT NULL,
    enabled_at DATETIME(6),
    last_used_step BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_admin_mfa PRIMARY KEY (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 비밀번호를 확인한 관리자가 OTP를 넣을 때까지 쓰는 5분짜리 1회용 토큰. 원문이 아니라 서버 키로 만든 해시만 둔다.
CREATE TABLE admin_login_challenge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL COLLATE utf8mb4_0900_bin,
    token_version BIGINT NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_admin_login_challenge PRIMARY KEY (id),
    CONSTRAINT uk_admin_login_challenge_token_hash UNIQUE (token_hash),
    INDEX ix_admin_login_challenge_member_id (member_id),
    INDEX ix_admin_login_challenge_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 관리자 세션이 끝나는 시각. 재발급해도 이 시각을 넘기지 못한다. 회원 세션은 NULL
ALTER TABLE refresh_token ADD COLUMN session_expires_at DATETIME(6);
