-- #137: 테이블 기본 정렬 규칙(utf8mb4_0900_ai_ci)은 대소문자와 악센트를 구분하지 않는다.
-- 그래서 'ádmin@example.com'으로 조회해도 'admin@example.com' 회원이 나와, 이메일별 로그인 잠금을 변형 이메일로 피할 수 있었다.
-- 값으로 행을 찾는 컬럼은 글자를 그대로 비교하는 utf8mb4_0900_bin으로 바꾼다.
-- 이메일은 앱이 소문자로 맞춰 저장하므로 대소문자를 구분해도 로그인에는 영향이 없다.
ALTER TABLE member MODIFY email VARCHAR(255) NOT NULL COLLATE utf8mb4_0900_bin;
ALTER TABLE email_verification MODIFY email VARCHAR(255) NOT NULL COLLATE utf8mb4_0900_bin;

-- 클라이언트가 보낸 주문 멱등 키와 Toss 결제 키도 대소문자만 다른 값을 다른 값으로 본다.
ALTER TABLE orders MODIFY idempotency_key VARCHAR(100) COLLATE utf8mb4_0900_bin;
ALTER TABLE payment MODIFY payment_key VARCHAR(200) NOT NULL COLLATE utf8mb4_0900_bin;
