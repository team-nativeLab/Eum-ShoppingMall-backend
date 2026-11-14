-- 상품 상세 화면 정보(#198): 상품 코드, 모델명, 한 줄 요약, 원산지, 배송 예정 안내, 설명 서식
ALTER TABLE product
    ADD COLUMN product_code VARCHAR(50),
    ADD COLUMN model_name VARCHAR(100),
    ADD COLUMN summary VARCHAR(300),
    ADD COLUMN country_of_origin VARCHAR(50),
    ADD COLUMN shipping_estimate VARCHAR(100),
    ADD COLUMN description_format VARCHAR(20) NOT NULL DEFAULT 'PLAIN',
    ADD CONSTRAINT uk_product_product_code UNIQUE (product_code);

-- 상세 갤러리. sort_order 순서로 보여 주며 첫 이미지가 대표 이미지(product.image_url)다.
CREATE TABLE product_image (
    product_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    url VARCHAR(500) NOT NULL,
    alt VARCHAR(200) NOT NULL,
    CONSTRAINT pk_product_image PRIMARY KEY (product_id, sort_order),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 사양 표(항목·값). 제조사·재질·크기·중량 등도 이 행으로 저장한다.
CREATE TABLE product_specification (
    product_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    spec_label VARCHAR(50) NOT NULL,
    spec_value VARCHAR(200) NOT NULL,
    CONSTRAINT pk_product_specification PRIMARY KEY (product_id, sort_order),
    CONSTRAINT fk_product_specification_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
