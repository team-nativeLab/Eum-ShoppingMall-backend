-- #143: 재고·수량·금액은 앱의 입력 검증과 조건부 UPDATE로만 지켜 왔다.
-- 코드 실수나 DB 직접 수정으로 음수 재고·음수 금액이 들어가지 않도록 DB도 막는다(MySQL 8.4는 CHECK를 실제로 검사한다).
-- 기존 행이 조건을 어기면 이 마이그레이션이 실패하므로, 배포 전에 조건을 어기는 행이 있는지 먼저 확인한다.
-- 제약 이름은 ck_<테이블>_<컬럼>으로 짓는다. 위반은 DataIntegrityViolationException(409 CONCURRENT_UPDATE)이 된다.
ALTER TABLE product
    ADD CONSTRAINT ck_product_base_price CHECK (base_price >= 0),
    ADD CONSTRAINT ck_product_list_price CHECK (list_price >= 0);

ALTER TABLE product_sku
    ADD CONSTRAINT ck_product_sku_price CHECK (price >= 0),
    ADD CONSTRAINT ck_product_sku_stock CHECK (stock >= 0);

ALTER TABLE cart_item
    ADD CONSTRAINT ck_cart_item_quantity CHECK (quantity >= 1);

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_total_amount CHECK (total_amount >= 0),
    ADD CONSTRAINT ck_orders_item_amount CHECK (item_amount >= 0),
    ADD CONSTRAINT ck_orders_discount_amount CHECK (discount_amount >= 0),
    ADD CONSTRAINT ck_orders_shipping_fee CHECK (shipping_fee >= 0);

ALTER TABLE order_item
    ADD CONSTRAINT ck_order_item_order_price CHECK (order_price >= 0),
    ADD CONSTRAINT ck_order_item_quantity CHECK (quantity >= 1);

ALTER TABLE payment
    ADD CONSTRAINT ck_payment_approved_amount CHECK (approved_amount >= 0),
    ADD CONSTRAINT ck_payment_balance_amount CHECK (balance_amount >= 0);

ALTER TABLE shipping_policy
    ADD CONSTRAINT ck_shipping_policy_shipping_fee CHECK (shipping_fee >= 0),
    ADD CONSTRAINT ck_shipping_policy_free_shipping_threshold CHECK (free_shipping_threshold >= 0);
