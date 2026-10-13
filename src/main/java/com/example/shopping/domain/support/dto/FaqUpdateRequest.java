package com.example.shopping.domain.support.dto;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 항목만 수정한다. */
public record FaqUpdateRequest(
		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "분류를 입력해 주세요.")
		@Size(max = 50, message = "분류는 50자 이하여야 합니다.")
		String category,

		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "질문을 입력해 주세요.")
		@Size(max = 300, message = "질문은 300자 이하여야 합니다.")
		String question,

		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "답변을 입력해 주세요.")
		@Size(max = 5000, message = "답변은 5000자 이하여야 합니다.")
		String answer,

		Integer sortOrder) {
}
