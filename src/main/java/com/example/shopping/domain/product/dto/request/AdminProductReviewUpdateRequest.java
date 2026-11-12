package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 관리자 리뷰 처리. 보낸 항목만 바꾼다.
 *
 * @param visible    false면 목록과 평점에서 뺀다.
 * @param adminReply 고객에게 보이는 답변. 빈 문자열이면 지운다.
 * @param adminMemo  고객에게 보이지 않는 메모. 빈 문자열이면 지운다.
 */
public record AdminProductReviewUpdateRequest(
		Boolean visible,

		@Size(max = 2000, message = "답변은 2,000자 이하여야 합니다.")
		String adminReply,

		@Size(max = 2000, message = "메모는 2,000자 이하여야 합니다.")
		String adminMemo) {
}
