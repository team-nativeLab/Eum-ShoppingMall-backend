package com.example.shopping.domain.home.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 고정 콘텐츠 저장
 *
 * @param title 제목(200자 이하)
 * @param body  평문 본문(100,000자 이하). HTML 태그는 받지 않는다.
 */
public record SiteContentUpsertRequest(
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 100_000) String body) {
}
