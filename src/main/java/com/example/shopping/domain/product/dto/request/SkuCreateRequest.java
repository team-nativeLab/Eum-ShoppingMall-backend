package com.example.shopping.domain.product.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 관리자가 새 상품 옵션을 추가할 때 보낸다.
 *
 * @param optionName 옵션 이름(100자 이하)
 * @param price      옵션 판매가(원). 기본가에 더하는 금액이 아니라 장바구니·주문에 그대로 쓰는 최종 단가다.
 * @param stock      처음 재고 수량
 */
public record SkuCreateRequest(
		@NotBlank(message = "옵션 이름을 입력해 주세요.")
		@Size(max = 100, message = "옵션 이름은 100자 이하여야 합니다.")
		String optionName,

		@NotNull(message = "옵션 가격을 입력해 주세요.")
		@PositiveOrZero(message = "옵션 가격은 0 이상이어야 합니다.")
		@Max(value = ValidationPatterns.MAX_PRICE, message = "옵션 가격이 너무 큽니다.")
		Long price,

		@NotNull(message = "재고를 입력해 주세요.")
		@Min(value = 0, message = "재고는 0 이상이어야 합니다.")
		@Max(value = 1_000_000_000, message = "재고는 10억 개 이하여야 합니다.")
		Integer stock) {
}
