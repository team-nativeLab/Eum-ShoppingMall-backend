package com.example.shopping.domain.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param sortOrder 생략하면 0
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

		Integer sortOrder) {
}
