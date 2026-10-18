package com.example.shopping.domain.product.dto.response;

import com.example.shopping.domain.product.entity.Category;

/**
 * @param slug        주소에 쓰는 영문 이름 (예: /category/head-protection)
 * @param description 짧은 소개. 없으면 null
 * @param iconKey     아이콘 이름. 없으면 null
 */
public record CategoryResponse(Long id, Long parentId, String name, String slug, String description,
		String iconKey) {

	public static CategoryResponse from(Category category) {
		Long parentId = category.getParent() == null ? null : category.getParent().getId();
		return new CategoryResponse(category.getId(), parentId, category.getName(), category.getSlug(),
				category.getDescription(), category.getIconKey());
	}
}
