package com.example.shopping.domain.product.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductSkuRequest(
		@NotBlank(message = "옵션 이름을 입력해 주세요.")
		@Size(max = 100, message = "옵션 이름은 100자 이하여야 합니다.")
		String optionName,

		@NotNull(message = "옵션 가격을 입력해 주세요.")
		@PositiveOrZero(message = "옵션 가격은 0 이상이어야 합니다.")
		@Max(value = ValidationPatterns.MAX_PRICE, message = "옵션 가격이 너무 큽니다.")
		Long price,

		@NotNull(message = "재고를 입력해 주세요.")
		@PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
		Integer stock) {
}
