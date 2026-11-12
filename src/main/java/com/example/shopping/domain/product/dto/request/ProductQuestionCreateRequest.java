package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 상품 문의 작성
 *
 * @param isPrivate true면 비밀 문의. 작성자 본인과 관리자만 내용과 답변을 본다.
 */
public record ProductQuestionCreateRequest(
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 100, message = "제목은 100자 이하여야 합니다.")
		String title,

		@NotBlank(message = "문의 내용을 입력해 주세요.")
		@Size(max = 2000, message = "문의 내용은 2,000자 이하여야 합니다.")
		String content,

		Boolean isPrivate) {
}
