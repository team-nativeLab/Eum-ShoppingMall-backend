package com.example.shopping.domain.order.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.order.entity.ShippingPolicy;

/**
 * @param shippingFee           배송비(원)
 * @param freeShippingThreshold 상품 금액이 이 값 이상이면 무료배송(원). null이면 금액과 관계없이 배송비를 받는다.
 * @param updatedAt             관리자가 마지막으로 바꾼 시각. 아직 기본값이면 null
 */
public record ShippingPolicyResponse(long shippingFee, Long freeShippingThreshold, LocalDateTime updatedAt) {

	public static ShippingPolicyResponse from(ShippingPolicy policy) {
		return new ShippingPolicyResponse(policy.getShippingFee(), policy.getFreeShippingThreshold(),
				policy.getUpdatedAt());
	}
}
