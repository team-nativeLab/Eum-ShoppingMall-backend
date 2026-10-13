package com.example.shopping.domain.home.dto;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param imageUrl  파일 업로드 API가 돌려준 주소
 * @param linkUrl   배너를 눌렀을 때 이동할 주소. 없으면 생략
 * @param sortOrder 생략하면 0
 * @param visible   생략하면 노출(true)
 */
public record BannerCreateRequest(
		@NotBlank(message = "이미지 주소를 입력해 주세요.")
		@Pattern(regexp = ValidationPatterns.URL_OR_PATH, message = "이미지 주소 형식이 올바르지 않습니다.")
		@Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
		String imageUrl,

		@Pattern(regexp = ValidationPatterns.URL_OR_PATH, message = "연결 주소 형식이 올바르지 않습니다.")
		@Size(max = 500, message = "연결 주소는 500자 이하여야 합니다.")
		String linkUrl,

		Integer sortOrder,

		Boolean visible) {
}
