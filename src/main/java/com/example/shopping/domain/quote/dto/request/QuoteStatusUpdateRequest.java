package com.example.shopping.domain.quote.dto.request;

import com.example.shopping.domain.quote.entity.QuoteStatus;

import jakarta.validation.constraints.NotNull;

/**
 * 견적 문의 처리 상태 변경
 *
 * @param status 바꿀 처리 상태. RECEIVED(접수), IN_PROGRESS(처리 중), COMPLETED(완료)
 */
public record QuoteStatusUpdateRequest(
		@NotNull(message = "처리 상태를 입력해 주세요.")
		QuoteStatus status) {
}
