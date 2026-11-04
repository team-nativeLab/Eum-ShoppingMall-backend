-- 회원가입 때 받은 동의 기록(#21). 이용약관·개인정보 수집은 필수, 마케팅 수신은 선택이다.
-- document_version은 동의한 시점에 보여 준 고정 콘텐츠(terms, privacy-policy)의 수정 시각이다.
CREATE TABLE member_consent (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    member_id BIGINT NOT NULL,
    consent_type VARCHAR(20) NOT NULL,
    agreed BIT(1) NOT NULL,
    document_version VARCHAR(40),
    CONSTRAINT pk_member_consent PRIMARY KEY (id),
    CONSTRAINT fk_member_consent_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_member_consent_member ON member_consent (member_id, consent_type);

-- 비회원이 1:1 문의·견적을 접수할 때 받은 개인정보 수집 동의 시각과 그때의 개인정보 처리방침 버전
ALTER TABLE inquiry
    ADD COLUMN privacy_agreed_at DATETIME(6),
    ADD COLUMN privacy_policy_version VARCHAR(40);

ALTER TABLE quote_inquiry
    ADD COLUMN privacy_agreed_at DATETIME(6),
    ADD COLUMN privacy_policy_version VARCHAR(40);
