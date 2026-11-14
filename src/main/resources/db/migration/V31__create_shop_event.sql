-- 이벤트·기획전. 진행 상태(진행 중·예정·종료)는 저장하지 않고 기간과 현재 시각으로 정한다.
-- hero_title은 상세 화면 큰 제목의 줄들을 줄바꿈(\n)으로 이어 저장한다.
CREATE TABLE shop_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    title VARCHAR(100) NOT NULL,
    subtitle VARCHAR(200) NOT NULL,
    eyebrow VARCHAR(50) NOT NULL,
    hero_title VARCHAR(200) NOT NULL,
    hero_description VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    search_keyword VARCHAR(50) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    visible BIT(1) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT pk_shop_event PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 이벤트 상세 본문 구성(소제목·본문). 순서(sort_order)대로 보여 주고, 이벤트를 지우면 함께 지운다.
CREATE TABLE shop_event_section (
    event_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    heading VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    CONSTRAINT pk_shop_event_section PRIMARY KEY (event_id, sort_order),
    CONSTRAINT fk_shop_event_section_event FOREIGN KEY (event_id) REFERENCES shop_event (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
