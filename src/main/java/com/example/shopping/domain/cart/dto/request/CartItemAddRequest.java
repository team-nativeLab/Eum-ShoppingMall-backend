package com.example.shopping.domain.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemAddRequest(
		@NotNull(message = "옵션을 선택해 주세요.")
		Long skuId,

		@NotNull(message = "수량을 입력해 주세요.")
		@Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
		Integer quantity) {
}
