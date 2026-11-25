package com.example.shopping.domain.support.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 관리자 1:1 문의 답변·메모(#223). 보낸 항목만 바꾼다.
 *
 * @param answer    고객에게 보이는 답변. 보내면 답변완료로 바꾸고 고객에게 알린다. 빈 문자열이면 답변을 지우고 접수 상태로 되돌린다.
 * @param adminMemo 고객에게 보이지 않는 내부 메모. 빈 문자열이면 지운다.
 */
public record AdminInquiryUpdateRequest(
		@Size(max = 5000, message = "답변은 5000자 이하여야 합니다.")
		String answer,

		@Size(max = 2000, message = "메모는 2,000자 이하여야 합니다.")
		String adminMemo) {
}
