package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param delta  늘릴 수량(입고)은 양수, 줄일 수량(파손·분실·실사 차이)은 음수. 현재 재고에 더한다.
 * @param reason 조정 사유 (예: 입고, 파손, 실사 차이). 관리자 변경 기록에 남는다.
 */
public record StockAdjustmentRequest(
		@NotNull(message = "조정 수량을 입력해 주세요.")
		@Min(value = -1_000_000, message = "한 번에 100만 개까지 조정할 수 있습니다.")
		@Max(value = 1_000_000, message = "한 번에 100만 개까지 조정할 수 있습니다.")
		Integer delta,

		@Size(max = 200, message = "조정 사유는 200자 이하여야 합니다.")
		String reason) {
}
