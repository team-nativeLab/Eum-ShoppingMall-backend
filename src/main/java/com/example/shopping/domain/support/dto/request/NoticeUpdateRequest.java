package com.example.shopping.domain.support.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 항목만 수정한다. */
public record NoticeUpdateRequest(
		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "제목을 입력해 주세요.")
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.")
		String title,

		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "내용을 입력해 주세요.")
		@Size(max = 20000, message = "내용은 20000자 이하여야 합니다.")
		String content,

		Boolean pinned,

		Boolean visible,

		@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
		@Max(value = 9999, message = "정렬 순서는 9999 이하여야 합니다.")
		Integer sortOrder) {
}
