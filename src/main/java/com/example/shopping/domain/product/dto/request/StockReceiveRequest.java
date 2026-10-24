package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param quantity 입고 수량. 현재 재고에 이만큼 더한다.
 */
public record StockReceiveRequest(
		@NotNull(message = "입고 수량을 입력해 주세요.")
		@Min(value = 1, message = "입고 수량은 1개 이상이어야 합니다.")
		@Max(value = 1_000_000, message = "한 번에 100만 개까지 입고할 수 있습니다.")
		Integer quantity) {
}
