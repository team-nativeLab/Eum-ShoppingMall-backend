-- #92·#89 이후 코드가 쓰지 않는 컬럼과 테이블을 지운다(#97).
-- 이 마이그레이션 뒤에는 그 이전 버전 서버를 띄우면 Hibernate 스키마 검증에 실패한다.
ALTER TABLE member
    DROP COLUMN failed_login_count,
    DROP COLUMN locked_until;

-- 배송지 사전 저장(POST /api/v1/checkout/delivery) 기능을 없앴다. 남은 배송지 개인정보도 함께 지운다.
DROP TABLE checkout_delivery;
