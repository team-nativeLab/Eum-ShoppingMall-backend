package com.example.shopping.domain.order.entity;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 1단계에서 입력한 배송지. 회원당 하나만 두고 다시 입력하면 덮어쓴다. 주문을 만들 때 주문서로 복사한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckoutDelivery {

	@Id
	private Long memberId;

	@Embedded
	private ShippingAddress address;

	public static CheckoutDelivery create(Long memberId, ShippingAddress address) {
		CheckoutDelivery delivery = new CheckoutDelivery();
		delivery.memberId = memberId;
		delivery.address = address;
		return delivery;
	}

	public void change(ShippingAddress address) {
		this.address = address;
	}
}
