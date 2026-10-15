package com.example.shopping.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 최상위 카테고리는 null
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Category parent;

	@Column(nullable = false, length = 100)
	private String name;

	// 주소에 쓰는 영문 이름 (예: /category/head-protection). 서로 겹칠 수 없다.
	@Column(unique = true, length = 60)
	private String slug;

	// 전체 메뉴 등에 보여 줄 짧은 소개 문구
	@Column(length = 200)
	private String description;

	// 프론트가 아이콘을 고를 때 쓰는 이름 (예: hard-hat)
	@Column(length = 50)
	private String iconKey;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean active = true;

	public static Category create(Category parent, String name, String slug, int sortOrder, boolean active) {
		Category category = new Category();
		category.parent = parent;
		category.name = name;
		category.slug = slug;
		category.sortOrder = sortOrder;
		category.active = active;
		return category;
	}

	/** null인 항목은 바꾸지 않는다. 소개 문구와 아이콘 이름은 빈 문자열을 주면 지운다. */
	public void update(String name, String slug, String description, String iconKey, Integer sortOrder,
			Boolean active) {
		if (name != null) {
			this.name = name;
		}
		if (slug != null) {
			this.slug = slug;
		}
		if (description != null) {
			this.description = description.isBlank() ? null : description.trim();
		}
		if (iconKey != null) {
			this.iconKey = iconKey.isBlank() ? null : iconKey.trim();
		}
		if (sortOrder != null) {
			this.sortOrder = sortOrder;
		}
		if (active != null) {
			this.active = active;
		}
	}
}
