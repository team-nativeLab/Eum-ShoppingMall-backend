package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.NotNull;

public record PriceVisibilityRequest(
		@NotNull(message = "가격 공개 여부를 입력해 주세요.")
		Boolean priceVisible) {
}
