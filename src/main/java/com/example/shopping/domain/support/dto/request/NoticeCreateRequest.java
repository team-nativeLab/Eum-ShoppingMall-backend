package com.example.shopping.domain.support.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param pinned  상단 고정. 생략하면 false
 * @param visible   공개 여부. 생략하면 공개(true)
 * @param sortOrder 작을수록 앞(고정 공지 안팎에서 각각). 생략하면 0
 */
public record NoticeCreateRequest(
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.")
		String title,

		@NotBlank(message = "내용을 입력해 주세요.")
		@Size(max = 20000, message = "내용은 20000자 이하여야 합니다.")
		String content,

		Boolean pinned,

		Boolean visible,

		@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
		@Max(value = 9999, message = "정렬 순서는 9999 이하여야 합니다.")
		Integer sortOrder) {
}
