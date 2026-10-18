package com.example.shopping.domain.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param pinned  상단 고정. 생략하면 false
 * @param visible 공개 여부. 생략하면 공개(true)
 */
public record NoticeCreateRequest(
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.")
		String title,

		@NotBlank(message = "내용을 입력해 주세요.")
		@Size(max = 20000, message = "내용은 20000자 이하여야 합니다.")
		String content,

		Boolean pinned,

		Boolean visible) {
}
