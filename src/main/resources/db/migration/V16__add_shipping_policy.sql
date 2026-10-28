-- 관리자가 바꾸는 배송비 정책(한 행). 행이 없으면 애플리케이션 기본값(10만 원 미만 배송비 5,000원)을 쓴다.
CREATE TABLE shipping_policy (
    id BIGINT NOT NULL,
    shipping_fee BIGINT NOT NULL,
    free_shipping_threshold BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_shipping_policy PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
