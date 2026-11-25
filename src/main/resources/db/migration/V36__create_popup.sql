-- 팝업(#211): 웹 홈 첫 화면에 띄우는 안내. 사진은 없어도 된다.
-- 고객에게는 노출하는 팝업만 정렬 순서대로 보낸다.
CREATE TABLE popup (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    title VARCHAR(100) NOT NULL,
    image_url VARCHAR(500),
    body VARCHAR(2000) NOT NULL,
    visible BIT(1) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT pk_popup PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
