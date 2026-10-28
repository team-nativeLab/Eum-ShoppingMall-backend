package com.example.shopping.domain.order.dto.request;

import com.example.shopping.domain.order.entity.Order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param shippingFee           배송비(원). 0이면 항상 무료배송
 * @param freeShippingThreshold 상품 금액이 이 값 이상이면 무료배송(원). 비우면 금액과 관계없이 배송비를 받는다.
 */
public record ShippingPolicyRequest(
		@NotNull(message = "배송비를 입력해 주세요.")
		@PositiveOrZero(message = "배송비는 0원 이상이어야 합니다.")
		@Max(value = 1_000_000, message = "배송비는 100만 원 이하로 입력해 주세요.")
		Long shippingFee,

		@Positive(message = "무료배송 기준 금액은 1원 이상이어야 합니다.")
		@Max(value = Order.MAX_TOTAL_AMOUNT, message = "무료배송 기준 금액이 너무 큽니다.")
		Long freeShippingThreshold) {
}
