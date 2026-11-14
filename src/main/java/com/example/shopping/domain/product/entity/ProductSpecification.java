package com.example.shopping.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 상세 사양 표의 한 행(예: 재질 / ABS). 제조사·재질·크기·중량 등도 이 행으로 저장한다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSpecification {

	@Column(name = "spec_label", nullable = false, length = 50)
	private String label;

	@Column(name = "spec_value", nullable = false, length = 200)
	private String value;

	public ProductSpecification(String label, String value) {
		this.label = label;
		this.value = value;
	}
}
