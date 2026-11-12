package com.example.shopping.domain.member.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 저장해 두는 배송지(주소록). 주문은 이 값을 배송지 스냅샷(ShippingAddress)으로 복사해 저장하므로,
 * 주소록을 고치거나 지워도 지난 주문의 배송지는 그대로다. 탈퇴하면 모두 지운다.
 */
@Entity
@Table(indexes = @Index(columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAddress extends BaseTimeEntity {

	/** 회원 한 명이 저장할 수 있는 배송지 수 */
	public static final int MAX_PER_MEMBER = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	// 배송지 이름(예: 회사, 현장)
	@Column(nullable = false, length = 30)
	private String label;

	@Column(nullable = false, length = 50)
	private String recipient;

	@Column(nullable = false, length = 30)
	private String recipientPhone;

	@Column(nullable = false, length = 10)
	private String zipCode;

	@Column(nullable = false, length = 200)
	private String address;

	@Column(length = 200)
	private String addressDetail;

	// 주문서에서 먼저 고르는 기본 배송지. 회원마다 하나다.
	@Column(name = "default_address", nullable = false)
	private boolean defaultAddress;

	public static MemberAddress create(Long memberId, Fields fields, boolean defaultAddress) {
		MemberAddress memberAddress = new MemberAddress();
		memberAddress.memberId = memberId;
		memberAddress.change(fields);
		memberAddress.defaultAddress = defaultAddress;
		return memberAddress;
	}

	public void change(Fields fields) {
		this.label = fields.label();
		this.recipient = fields.recipient();
		this.recipientPhone = fields.recipientPhone();
		this.zipCode = fields.zipCode();
		this.address = fields.address();
		this.addressDetail = fields.addressDetail();
	}

	public void makeDefault() {
		this.defaultAddress = true;
	}

	/** 저장할 값. 앞뒤 공백과 연락처의 하이픈은 서비스에서 정리해 넘긴다. */
	public record Fields(String label, String recipient, String recipientPhone, String zipCode, String address,
			String addressDetail) {
	}
}
