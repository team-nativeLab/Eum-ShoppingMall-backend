-- 브랜드 소개(브랜드 목록·상세 페이지). 상품과는 이름으로 이어진다: product.brand 값이 brand.name과 같으면 그 브랜드의 상품이다.
-- 이름으로 행을 찾으므로 글자를 그대로 비교한다(#137).
CREATE TABLE brand (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    name VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_bin,
    english_name VARCHAR(100),
    logo_url VARCHAR(500),
    tagline VARCHAR(200),
    cover_image_url VARCHAR(500),
    main_field VARCHAR(100),
    origin_country VARCHAR(50),
    certification VARCHAR(200),
    story_title VARCHAR(100),
    story_body TEXT,
    visible BIT(1) NOT NULL,
    featured BIT(1) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT pk_brand PRIMARY KEY (id),
    CONSTRAINT uk_brand_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 브랜드별 상품 수와 브랜드 상품 목록(?brand=)에 쓴다.
CREATE INDEX ix_product_brand_status ON product (brand, status);
