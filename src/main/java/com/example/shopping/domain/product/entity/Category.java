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

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean active = true;

	public static Category create(Category parent, String name, int sortOrder, boolean active) {
		Category category = new Category();
		category.parent = parent;
		category.name = name;
		category.sortOrder = sortOrder;
		category.active = active;
		return category;
	}

	/** null인 항목은 바꾸지 않는다. */
	public void update(String name, Integer sortOrder, Boolean active) {
		if (name != null) {
			this.name = name;
		}
		if (sortOrder != null) {
			this.sortOrder = sortOrder;
		}
		if (active != null) {
			this.active = active;
		}
	}
}
