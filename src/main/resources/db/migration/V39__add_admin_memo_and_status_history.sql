-- 관리자 내부 메모와 상태 변경 이력(#223).
-- 주문·1:1 문의의 내부 메모는 고객에게 보이지 않는다. 개인정보가 섞일 수 있어 보관 기간이 지나면 다른 개인정보와 함께 지운다.
ALTER TABLE orders ADD COLUMN admin_memo VARCHAR(2000) NOT NULL DEFAULT '';
ALTER TABLE inquiry ADD COLUMN admin_memo VARCHAR(2000) NOT NULL DEFAULT '';

-- 주문·1:1 문의·상품 문의의 상태가 바뀐 기록. 대상(target_type, target_id)마다 쌓고 고치지 않는다.
-- note에는 정해 둔 문구만 넣는다(개인정보 없음).
CREATE TABLE status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    note VARCHAR(100) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_status_history PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_status_history_target ON status_history (target_type, target_id, id);

-- 이미 있는 데이터는 남아 있는 시각으로 채운다. 대상마다 앞의 문장이 먼저 들어가므로 id 순서가 시간 순서다.
INSERT INTO status_history (target_type, target_id, status, note, occurred_at)
SELECT 'ORDER', id, 'PENDING', '주문 접수', created_at FROM orders;
INSERT INTO status_history (target_type, target_id, status, note, occurred_at)
SELECT 'ORDER', id, 'PAID', '결제 완료', paid_at FROM orders WHERE paid_at IS NOT NULL;
INSERT INTO status_history (target_type, target_id, status, note, occurred_at)
SELECT 'ORDER', id, 'CANCELED', '취소 완료', cancelled_at FROM orders
WHERE status = 'CANCELED' AND cancelled_at IS NOT NULL;
INSERT INTO status_history (target_type, target_id, status, note, occurred_at)
SELECT 'INQUIRY', id, 'RECEIVED', '문의 접수', created_at FROM inquiry;
INSERT INTO status_history (target_type, target_id, status, note, occurred_at)
SELECT 'INQUIRY', id, 'ANSWERED', '답변 등록', updated_at FROM inquiry WHERE status = 'ANSWERED';
INSERT INTO status_history (target_type, target_id, status, note, occurred_at)
SELECT 'PRODUCT_QUESTION', id, 'WAITING', '문의 등록', created_at FROM product_question;
INSERT INTO status_history (target_type, target_id, status, note, occurred_at)
SELECT 'PRODUCT_QUESTION', id, 'ANSWERED', '답변 등록', answered_at FROM product_question
WHERE answered_at IS NOT NULL;
