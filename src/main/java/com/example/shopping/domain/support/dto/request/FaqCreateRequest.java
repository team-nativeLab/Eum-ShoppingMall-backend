package com.example.shopping.domain.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FAQ 등록
 *
 * @param category  분류(50자 이하, 예: 배송)
 * @param question  질문(300자 이하)
 * @param answer    답변(5,000자 이하)
 * @param sortOrder 생략하면 0
 * @param visible   고객 FAQ 목록에 보일지. 생략하면 노출(true)
 */
public record FaqCreateRequest(
		@NotBlank(message = "분류를 입력해 주세요.")
		@Size(max = 50, message = "분류는 50자 이하여야 합니다.")
		String category,

		@NotBlank(message = "질문을 입력해 주세요.")
		@Size(max = 300, message = "질문은 300자 이하여야 합니다.")
		String question,

		@NotBlank(message = "답변을 입력해 주세요.")
		@Size(max = 5000, message = "답변은 5000자 이하여야 합니다.")
		String answer,

		Integer sortOrder,

		Boolean visible) {
}
