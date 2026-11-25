-- FAQ 노출 여부와 공지 정렬 순서(#220). 이미 있는 FAQ는 노출, 공지는 0번이다.
ALTER TABLE faq ADD COLUMN visible BIT(1) NOT NULL DEFAULT b'1';
ALTER TABLE notice ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
