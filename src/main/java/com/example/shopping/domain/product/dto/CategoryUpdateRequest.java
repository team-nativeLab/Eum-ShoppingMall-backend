package com.example.shopping.domain.product.dto;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 항목만 수정한다. */
public record CategoryUpdateRequest(
		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "카테고리 이름을 입력해 주세요.")
		@Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
		String name,

		Integer sortOrder,

		Boolean active) {
}
