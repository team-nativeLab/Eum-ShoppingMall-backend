package com.example.shopping.domain.product.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 옵션 이름과 가격만 바꾼다. 재고 변경은 입고·재고 조정 API로 처리한다.
 *
 * @param optionName 옵션 이름(100자 이하)
 * @param price      옵션 판매가(원). 기본가에 더하는 금액이 아니라 장바구니·주문에 그대로 쓰는 최종 단가다.
 */
public record SkuUpdateRequest(
		@NotBlank(message = "옵션 이름을 입력해 주세요.")
		@Size(max = 100, message = "옵션 이름은 100자 이하여야 합니다.")
		String optionName,

		@NotNull(message = "옵션 가격을 입력해 주세요.")
		@PositiveOrZero(message = "옵션 가격은 0 이상이어야 합니다.")
		@Max(value = ValidationPatterns.MAX_PRICE, message = "옵션 가격이 너무 큽니다.")
		Long price) {
}
