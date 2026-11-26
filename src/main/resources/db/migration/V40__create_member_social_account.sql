-- 카카오·구글 로그인(#225). 회원 하나에 제공자마다 계정 하나를 연결한다.
-- provider_user_id는 카카오 회원번호, 구글 sub 값이다. email은 연결할 때 제공자가 알려 준 이메일이다.
CREATE TABLE member_social_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    member_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    CONSTRAINT pk_member_social_account PRIMARY KEY (id),
    CONSTRAINT uk_member_social_account_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_member_social_account_member_provider UNIQUE (member_id, provider),
    CONSTRAINT fk_member_social_account_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 처음 소셜 로그인한 사람의 가입 대기(10분). 약관 동의를 받아 가입을 끝낼 때 한 번만 쓴다.
-- 토큰 원문은 저장하지 않고 용도별 키의 HMAC만 저장한다(Refresh 토큰과 같다). 지난 행은 정기 작업이 지운다.
CREATE TABLE social_signup_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    email_verified BIT(1) NOT NULL,
    name VARCHAR(50),
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_social_signup_ticket PRIMARY KEY (id),
    CONSTRAINT uk_social_signup_ticket_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_social_signup_ticket_expires_at ON social_signup_ticket (expires_at);

-- 지난 가입 대기를 지우는 정기 작업(SocialSignupTicketCleaner)의 잠금
INSERT INTO scheduler_lock (lock_name, lock_until, lock_owner, last_order_id)
VALUES ('purgeSocialSignupTickets', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND), NULL, 0);
