-- 회원 배송지 주소록. 주문은 배송지를 스냅샷으로 따로 저장하므로 주소록을 고치거나 지워도 지난 주문은 그대로다.
-- 탈퇴하면 그 회원의 배송지를 모두 지운다(MemberService.withdraw).
CREATE TABLE member_address (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    member_id BIGINT NOT NULL,
    label VARCHAR(30) NOT NULL,
    recipient VARCHAR(50) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    address VARCHAR(200) NOT NULL,
    address_detail VARCHAR(200),
    default_address BIT(1) NOT NULL,
    CONSTRAINT pk_member_address PRIMARY KEY (id),
    INDEX ix_member_address_member_id (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
