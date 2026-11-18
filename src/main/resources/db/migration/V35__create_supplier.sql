-- 발주처(#208): 상품을 받아 오는 거래처와 주문(발주) 정보를 받는 방법. 담당자 연락처는 관리자만 본다.
-- 주문 수신 방식(order_channel)이 EMAIL이면 order_email, FAX면 order_fax를 쓴다.
CREATE TABLE supplier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    contact_name VARCHAR(50) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    contact_email VARCHAR(100) NOT NULL,
    order_channel VARCHAR(10) NOT NULL,
    order_email VARCHAR(100),
    order_fax VARCHAR(30),
    address VARCHAR(200) NOT NULL,
    memo VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_supplier PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 상품의 발주처. 상품에 연결된 발주처는 지우지 못한다.
ALTER TABLE product
    ADD COLUMN supplier_id BIGINT,
    ADD CONSTRAINT fk_product_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id);
