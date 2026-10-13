package com.example.shopping.domain.product.repository;

import java.util.Collection;
import java.util.List;

import com.example.shopping.domain.product.entity.ProductStatus;

/**
 * 고객용 상품 검색 조건. statuses만 필수이고 나머지는 값이 있을 때만 적용한다.
 *
 * @param keyword       상품명 또는 브랜드에 포함된 글자
 * @param brands        이 중 하나의 브랜드인 상품만
 * @param minPrice      가격 하한(원, 이상)
 * @param maxPrice      가격 상한(원, 이하)
 * @param certifiedOnly true면 안전 인증 제품만
 */
public record ProductSearchCondition(Collection<ProductStatus> statuses, Long categoryId, String keyword,
		List<String> brands, Long minPrice, Long maxPrice, boolean certifiedOnly) {

	public boolean hasPriceRange() {
		return minPrice != null || maxPrice != null;
	}
}
