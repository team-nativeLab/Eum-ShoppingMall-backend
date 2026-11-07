-- #144: 입금이 끝났거나 기한이 지나 더 쓰지 않는 가상계좌는 번호 끝 4자리와 입금자명 첫 글자만 남긴다.
-- 앱도 입금 완료·기한 만료 때 같은 방식으로 가린다(Payment.maskVirtualAccount). 입금 대기 중인 결제는 그대로 둔다.
-- 결제 기록은 5년 동안 보관하므로, 그동안 계좌 번호와 입금자명이 그대로 남지 않게 한다.
UPDATE payment
SET virtual_account_number = CASE
        WHEN CHAR_LENGTH(virtual_account_number) <= 4 THEN REPEAT('*', CHAR_LENGTH(virtual_account_number))
        ELSE CONCAT(REPEAT('*', CHAR_LENGTH(virtual_account_number) - 4), RIGHT(virtual_account_number, 4))
    END,
    virtual_account_customer_name = CASE
        WHEN CHAR_LENGTH(virtual_account_customer_name) <= 1 THEN REPEAT('*', CHAR_LENGTH(virtual_account_customer_name))
        ELSE CONCAT(LEFT(virtual_account_customer_name, 1), REPEAT('*', CHAR_LENGTH(virtual_account_customer_name) - 1))
    END
WHERE status <> 'WAITING_DEPOSIT'
  AND virtual_account_number IS NOT NULL;
