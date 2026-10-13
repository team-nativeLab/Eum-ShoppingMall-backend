package com.example.shopping.domain.product.dto;

import com.example.shopping.domain.product.entity.Category;

public record CategoryResponse(Long id, Long parentId, String name) {

	public static CategoryResponse from(Category category) {
		Long parentId = category.getParent() == null ? null : category.getParent().getId();
		return new CategoryResponse(category.getId(), parentId, category.getName());
	}
}
