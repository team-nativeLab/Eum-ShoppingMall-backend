-- #134: 보유 기간이 지난 개인정보를 지운 시각. 정리 작업(PersonalDataRetentionCleaner)이 채우고, 채운 행은 다시 읽지 않는다.
-- 행 자체는 매출 통계(주문)와 동의 기록(문의·견적의 privacy_agreed_at, #21) 때문에 남긴다.
ALTER TABLE orders ADD COLUMN personal_data_purged_at DATETIME(6);
ALTER TABLE inquiry ADD COLUMN personal_data_purged_at DATETIME(6);
ALTER TABLE quote_inquiry ADD COLUMN personal_data_purged_at DATETIME(6);

-- 정리할 행(아직 지우지 않았고, 끝난 상태이며, 기간이 지난 것)만 찾게 한다.
CREATE INDEX ix_orders_personal_data_purge ON orders (personal_data_purged_at, status, updated_at);
CREATE INDEX ix_inquiry_personal_data_purge ON inquiry (personal_data_purged_at, status, updated_at);
CREATE INDEX ix_quote_inquiry_personal_data_purge ON quote_inquiry (personal_data_purged_at, status, updated_at);
CREATE INDEX ix_notification_status_updated ON notification_message (status, updated_at);
