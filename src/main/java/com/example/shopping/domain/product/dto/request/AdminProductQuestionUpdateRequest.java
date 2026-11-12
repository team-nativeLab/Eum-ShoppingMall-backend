package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 관리자 답변·메모. 보낸 항목만 바꾼다.
 *
 * @param answer    고객에게 보이는 답변. 빈 문자열이면 답변을 지우고 답변 대기로 되돌린다.
 * @param adminMemo 고객에게 보이지 않는 메모. 빈 문자열이면 지운다.
 */
public record AdminProductQuestionUpdateRequest(
		@Size(max = 2000, message = "답변은 2,000자 이하여야 합니다.")
		String answer,

		@Size(max = 2000, message = "메모는 2,000자 이하여야 합니다.")
		String adminMemo) {
}
