-- 상품 리뷰. 배송이 끝난 주문의 상품마다(주문 품목 하나에) 하나씩 쓴다. 관리자가 숨기면 목록과 평점에서 빠진다.
CREATE TABLE product_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    product_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content TEXT NOT NULL,
    option_label VARCHAR(100),
    visible BIT(1) NOT NULL,
    admin_reply TEXT,
    admin_replied_at DATETIME(6),
    admin_memo TEXT,
    CONSTRAINT pk_product_review PRIMARY KEY (id),
    CONSTRAINT uk_product_review_order_item UNIQUE (order_item_id),
    CONSTRAINT fk_product_review_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_product_review_order_item FOREIGN KEY (order_item_id) REFERENCES order_item (id),
    CONSTRAINT ck_product_review_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX ix_product_review_product_visible_created (product_id, visible, created_at),
    INDEX ix_product_review_member_id (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 보이는 리뷰 수와 별점 합계. 목록마다 리뷰를 세지 않도록 리뷰를 쓰거나 숨길 때 함께 바꾼다.
ALTER TABLE product ADD COLUMN review_count INT NOT NULL DEFAULT 0;
ALTER TABLE product ADD COLUMN rating_sum INT NOT NULL DEFAULT 0;
ALTER TABLE product ADD CONSTRAINT ck_product_review_count CHECK (review_count >= 0 AND rating_sum >= 0);
