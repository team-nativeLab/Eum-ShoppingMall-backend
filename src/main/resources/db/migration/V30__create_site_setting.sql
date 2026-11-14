-- 화면 하나의 설정을 통째로 저장하는 JSON 문서(홈 편성 home-layout, 사업자 정보 company-info).
-- 관리자가 한 번에 저장하고 고객 화면이 통째로 읽는 값이라 표로 나누지 않는다. 값의 모양은 애플리케이션의 record가 정한다.
CREATE TABLE site_setting (
    setting_key VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    value_json MEDIUMTEXT NOT NULL,
    CONSTRAINT pk_site_setting PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
