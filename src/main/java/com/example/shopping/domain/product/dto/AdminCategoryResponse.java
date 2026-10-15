package com.example.shopping.domain.product.dto;

import com.example.shopping.domain.product.entity.Category;

public record AdminCategoryResponse(Long id, Long parentId, String name, String slug, String description,
		String iconKey, int sortOrder, boolean active) {

	public static AdminCategoryResponse from(Category category) {
		Long parentId = category.getParent() == null ? null : category.getParent().getId();
		return new AdminCategoryResponse(category.getId(), parentId, category.getName(), category.getSlug(),
				category.getDescription(), category.getIconKey(), category.getSortOrder(), category.isActive());
	}
}
