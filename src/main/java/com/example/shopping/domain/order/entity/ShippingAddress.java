package com.example.shopping.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** WEB-015 배송지. 주문서에 스냅샷으로 저장한다. 값이 바뀌지 않는 객체라 여러 엔티티가 함께 참조해도 안전하다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress {

	@Column(length = 50)
	private String recipient;

	@Column(length = 30)
	private String recipientPhone;

	@Column(length = 10)
	private String zipCode;

	@Column(length = 200)
	private String address;

	@Column(length = 200)
	private String addressDetail;

	@Column(length = 200)
	private String deliveryRequest;

	public static ShippingAddress of(String recipient, String recipientPhone, String zipCode, String address,
			String addressDetail, String deliveryRequest) {
		ShippingAddress shippingAddress = new ShippingAddress();
		shippingAddress.recipient = recipient;
		shippingAddress.recipientPhone = recipientPhone;
		shippingAddress.zipCode = zipCode;
		shippingAddress.address = address;
		shippingAddress.addressDetail = addressDetail;
		shippingAddress.deliveryRequest = deliveryRequest;
		return shippingAddress;
	}
}
