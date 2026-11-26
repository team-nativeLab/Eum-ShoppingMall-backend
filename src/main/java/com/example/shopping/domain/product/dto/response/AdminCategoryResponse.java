package com.example.shopping.domain.product.dto.response;

import com.example.shopping.domain.product.entity.Category;

/**
 * 관리자 카테고리. 숨긴 카테고리도 들어 있다.
 *
 * @param id          카테고리 ID
 * @param parentId    상위 카테고리 ID. 최상위면 null
 * @param name        이름
 * @param slug        주소에 쓰는 영문 이름
 * @param description 짧은 소개. 없으면 null
 * @param iconKey     아이콘 이름. 없으면 null
 * @param sortOrder   정렬 순서. 작을수록 앞
 * @param active      고객 화면에 보이는지
 */
public record AdminCategoryResponse(Long id, Long parentId, String name, String slug, String description,
		String iconKey, int sortOrder, boolean active) {

	public static AdminCategoryResponse from(Category category) {
		Long parentId = category.getParent() == null ? null : category.getParent().getId();
		return new AdminCategoryResponse(category.getId(), parentId, category.getName(), category.getSlug(),
				category.getDescription(), category.getIconKey(), category.getSortOrder(), category.isActive());
	}
}
