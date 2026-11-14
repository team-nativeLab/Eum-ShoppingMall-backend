package com.example.shopping.domain.product.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 상품 상세 갤러리 이미지 한 장.
 *
 * @param url 업로드 API가 돌려준 주소
 * @param alt 대체 문구. 없으면 빈 문자열로 저장한다.
 */
public record ProductImageRequest(
		@NotBlank(message = "이미지 주소를 입력해 주세요.")
		@Pattern(regexp = ValidationPatterns.URL_OR_PATH, message = "이미지 주소 형식이 올바르지 않습니다.")
		@Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
		String url,

		@Size(max = 200, message = "이미지 설명은 200자 이하여야 합니다.")
		String alt) {
}
