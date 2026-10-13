package com.example.shopping.domain.home.dto;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 항목만 수정한다. 연결 주소는 빈 문자열을 보내면 지운다. */
public record BannerUpdateRequest(
		@Pattern(regexp = ValidationPatterns.URL_OR_PATH, message = "이미지 주소 형식이 올바르지 않습니다.")
		@Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
		String imageUrl,

		@Pattern(regexp = ValidationPatterns.URL_OR_PATH_OR_EMPTY, message = "연결 주소 형식이 올바르지 않습니다.")
		@Size(max = 500, message = "연결 주소는 500자 이하여야 합니다.")
		String linkUrl,

		Integer sortOrder,

		Boolean visible) {
}
