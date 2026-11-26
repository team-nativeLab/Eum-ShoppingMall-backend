package com.example.shopping.domain.cart.dto.request;

import jakarta.validation.constraints.Min;

/**
 * 보낸 항목만 바꾼다.
 *
 * @param quantity 바꿀 수량(1 이상). 생략하면 그대로
 * @param selected 주문할 품목으로 고를지. 생략하면 그대로
 */
public record CartItemUpdateRequest(
		@Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
		Integer quantity,

		Boolean selected) {
}
