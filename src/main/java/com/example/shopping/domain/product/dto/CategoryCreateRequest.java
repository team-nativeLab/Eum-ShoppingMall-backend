package com.example.shopping.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param parentId  최상위 카테고리면 생략
 * @param sortOrder 생략하면 0
 * @param active    생략하면 노출(true)
 */
public record CategoryCreateRequest(
		Long parentId,

		@NotBlank(message = "카테고리 이름을 입력해 주세요.")
		@Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
		String name,

		Integer sortOrder,

		Boolean active) {
}
