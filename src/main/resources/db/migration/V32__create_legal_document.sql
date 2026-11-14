-- 약관·정책 문서(이용약관 TERMS, 개인정보 처리방침 PRIVACY, 이용안내 GUIDE, 배송 안내 SHIPPING)의 버전.
-- 버전마다 한 행이며, 고객 화면은 시행일이 오늘이거나 지난 공개 버전 중 가장 최근 것을 본다.
-- 시행 중이거나 지난 공개 버전은 고치지 않고 새 버전으로 등록한다(이전 내용을 그대로 보관).
CREATE TABLE legal_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    version VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body MEDIUMTEXT NOT NULL,
    effective_at DATE NOT NULL,
    published BIT(1) NOT NULL,
    CONSTRAINT pk_legal_document PRIMARY KEY (id),
    CONSTRAINT uk_legal_document_type_version UNIQUE (document_type, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 종류별 시행 중인 공개 버전 찾기
CREATE INDEX ix_legal_document_type_effective ON legal_document (document_type, effective_at);
