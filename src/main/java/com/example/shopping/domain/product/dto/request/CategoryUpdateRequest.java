package com.example.shopping.domain.product.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 항목만 수정한다. 소개 문구와 아이콘 이름은 빈 문자열을 보내면 지운다. */
public record CategoryUpdateRequest(
		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "카테고리 이름을 입력해 주세요.")
		@Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
		String name,

		@Pattern(regexp = ValidationPatterns.SLUG, message = "주소 이름은 영문 소문자·숫자와 하이픈(-)만 쓸 수 있습니다.")
		@Size(max = 60, message = "주소 이름은 60자 이하여야 합니다.")
		String slug,

		@Size(max = 200, message = "소개 문구는 200자 이하여야 합니다.")
		String description,

		@Pattern(regexp = ValidationPatterns.SLUG_OR_EMPTY, message = "아이콘 이름은 영문 소문자·숫자와 하이픈(-)만 쓸 수 있습니다.")
		@Size(max = 50, message = "아이콘 이름은 50자 이하여야 합니다.")
		String iconKey,

		Integer sortOrder,

		Boolean active) {
}
