package com.example.shopping.domain.product.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 발주처(#208). 상품을 받아 오는 거래처와 주문(발주) 정보를 받는 방법. 담당자 연락처는 관리자만 본다. */
@Entity
@Table(name = "supplier")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String companyName;

	@Column(nullable = false, length = 50)
	private String contactName;

	@Column(nullable = false, length = 30)
	private String contactPhone;

	@Column(nullable = false, length = 100)
	private String contactEmail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private SupplierOrderChannel orderChannel;

	// 주문 수신 방식이 EMAIL일 때만 있다.
	@Column(length = 100)
	private String orderEmail;

	// 주문 수신 방식이 FAX일 때만 있다.
	@Column(length = 30)
	private String orderFax;

	// 없으면 빈 문자열
	@Column(nullable = false, length = 200)
	private String address;

	// 없으면 빈 문자열
	@Column(nullable = false, length = 1000)
	private String memo;

	public static Supplier create(Fields fields) {
		Supplier supplier = new Supplier();
		supplier.change(fields);
		return supplier;
	}

	/** 전체 수정 */
	public void change(Fields fields) {
		this.companyName = fields.companyName();
		this.contactName = fields.contactName();
		this.contactPhone = fields.contactPhone();
		this.contactEmail = fields.contactEmail();
		this.orderChannel = fields.orderChannel();
		this.orderEmail = fields.orderChannel() == SupplierOrderChannel.EMAIL ? fields.orderEmail() : null;
		this.orderFax = fields.orderChannel() == SupplierOrderChannel.FAX ? fields.orderFax() : null;
		this.address = fields.address();
		this.memo = fields.memo();
	}

	public record Fields(String companyName, String contactName, String contactPhone, String contactEmail,
			SupplierOrderChannel orderChannel, String orderEmail, String orderFax, String address, String memo) {
	}
}
