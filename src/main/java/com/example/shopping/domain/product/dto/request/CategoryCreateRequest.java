package com.example.shopping.domain.product.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param parentId    최상위 카테고리면 생략
 * @param slug        주소에 쓰는 영문 이름 (예: head-protection). 다른 카테고리와 겹칠 수 없다.
 * @param description 전체 메뉴 등에 보여 줄 짧은 소개. 없으면 생략
 * @param iconKey     프론트가 아이콘을 고를 때 쓰는 이름 (예: hard-hat). 없으면 생략
 * @param sortOrder   생략하면 0
 * @param active      생략하면 노출(true)
 */
public record CategoryCreateRequest(
		Long parentId,

		@NotBlank(message = "카테고리 이름을 입력해 주세요.")
		@Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
		String name,

		@NotBlank(message = "카테고리 주소 이름(slug)을 입력해 주세요.")
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
