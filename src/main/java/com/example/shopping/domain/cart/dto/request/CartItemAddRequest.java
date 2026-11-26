package com.example.shopping.domain.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 담기
 *
 * @param skuId    담을 옵션(SKU) ID. 상품 목록의 defaultSkuId나 상품 상세의 skus[].id
 * @param quantity 담을 수량(1 이상)
 */
public record CartItemAddRequest(
		@NotNull(message = "옵션을 선택해 주세요.")
		Long skuId,

		@NotNull(message = "수량을 입력해 주세요.")
		@Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
		Integer quantity) {
}
