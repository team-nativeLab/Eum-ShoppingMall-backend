-- 상품 상세의 상품 문의(Q&A). 회원이 남기고 관리자가 답한다. 비밀 문의는 작성자 본인과 관리자만 내용을 본다.
CREATE TABLE product_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    product_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    private_question BIT(1) NOT NULL,
    status VARCHAR(20) NOT NULL,
    answer TEXT,
    answered_at DATETIME(6),
    answered_by BIGINT,
    admin_memo TEXT,
    CONSTRAINT pk_product_question PRIMARY KEY (id),
    CONSTRAINT fk_product_question_product FOREIGN KEY (product_id) REFERENCES product (id),
    INDEX ix_product_question_product_created (product_id, created_at),
    INDEX ix_product_question_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
