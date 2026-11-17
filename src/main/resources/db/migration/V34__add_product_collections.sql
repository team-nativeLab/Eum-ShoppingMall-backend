-- 상품 목록 컬렉션(#207): 베스트(featured), 오늘의 상품(today_deal).
-- 고객 상품 목록의 ?collection=featured|today로 거른다. 홈의 베스트 영역은 홈 편성(site_setting)에서 고른 목록을 쓴다.
ALTER TABLE product
    ADD COLUMN featured BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN today_deal BIT(1) NOT NULL DEFAULT b'0';
