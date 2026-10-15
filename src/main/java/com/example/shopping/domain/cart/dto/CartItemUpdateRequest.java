package com.example.shopping.domain.cart.dto;

import jakarta.validation.constraints.Min;

/** 보낸 항목만 바꾼다. */
public record CartItemUpdateRequest(
		@Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
		Integer quantity,

		Boolean selected) {
}
