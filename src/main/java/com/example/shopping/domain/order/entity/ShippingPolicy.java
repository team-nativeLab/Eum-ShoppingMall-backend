package com.example.shopping.domain.order.entity;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 예상 금액과 주문 금액에 함께 쓰는 배송비 정책. 관리자가 바꾸며 한 행만 둔다.
 * 할인 정책은 없다. 정책을 바꿔도 이미 만든 주문의 금액은 그대로다(주문에 그 시점의 배송비를 저장한다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingPolicy extends BaseTimeEntity {

	public static final long ID = 1L;
	// 관리자가 정하기 전 기본값: 상품 금액 10만 원 미만이면 배송비 5,000원
	static final long DEFAULT_SHIPPING_FEE = 5_000;
	static final long DEFAULT_FREE_SHIPPING_THRESHOLD = 100_000;

	@Id
	private Long id;

	@Column(nullable = false)
	private long shippingFee;

	/** 상품 금액이 이 값 이상이면 무료배송. null이면 금액과 관계없이 배송비를 받는다. */
	private Long freeShippingThreshold;

	public static ShippingPolicy defaults() {
		ShippingPolicy policy = new ShippingPolicy();
		policy.id = ID;
		policy.change(DEFAULT_SHIPPING_FEE, DEFAULT_FREE_SHIPPING_THRESHOLD);
		return policy;
	}

	public void change(long shippingFee, Long freeShippingThreshold) {
		this.shippingFee = shippingFee;
		this.freeShippingThreshold = freeShippingThreshold;
	}

	/** 상품 금액에 붙는 배송비. 주문할 상품이 없거나 무료배송 기준 이상이면 0 */
	public long shippingFeeFor(long itemAmount) {
		if (itemAmount <= 0 || (freeShippingThreshold != null && itemAmount >= freeShippingThreshold)) {
			return 0;
		}
		return shippingFee;
	}
}
