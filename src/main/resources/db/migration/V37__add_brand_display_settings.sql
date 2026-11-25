-- 브랜드 대표 색상과 로고·커버 이미지 표시 방식(#214). 웹 브랜드 목록·선택 패널·상세가 이 값으로 그린다.
-- 맞춤 방식(*_fit)은 contain(전체 표시) 또는 cover(영역 채우기), 크기는 px이다. 이미 등록한 브랜드는 기본값을 쓴다.
ALTER TABLE brand
    ADD COLUMN accent_color VARCHAR(7) NOT NULL DEFAULT '#2F6F62',
    ADD COLUMN logo_fit VARCHAR(10) NOT NULL DEFAULT 'contain',
    ADD COLUMN logo_width INT NOT NULL DEFAULT 240,
    ADD COLUMN logo_height INT NOT NULL DEFAULT 96,
    ADD COLUMN cover_fit VARCHAR(10) NOT NULL DEFAULT 'cover',
    ADD COLUMN cover_width INT NOT NULL DEFAULT 1200,
    ADD COLUMN cover_height INT NOT NULL DEFAULT 704;
