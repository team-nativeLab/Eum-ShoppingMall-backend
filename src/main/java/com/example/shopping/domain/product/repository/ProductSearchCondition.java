package com.example.shopping.domain.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.example.shopping.domain.product.entity.Certification;
import com.example.shopping.domain.product.entity.ProductStatus;

/**
 * 고객용 상품 검색 조건. statuses만 필수이고 나머지는 값이 있을 때만 적용한다.
 *
 * @param categoryIds    이 카테고리들 중 하나에 속한 상품만 (상위 카테고리를 고르면 하위 카테고리까지 넣어 둔다). null이면 전체
 * @param keyword        상품명 또는 브랜드에 포함된 글자
 * @param brands         이 중 하나의 브랜드인 상품만
 * @param minPrice       가격 하한(원, 이상)
 * @param maxPrice       가격 상한(원, 이하)
 * @param certifications 이 중 하나라도 인증을 받은 상품만
 * @param certifiedOnly  true면 어떤 인증이든 하나 이상 받은 상품만
 * @param excludeSoldOut true면 품절 상품을 뺀다
 */
public record ProductSearchCondition(Collection<ProductStatus> statuses, Collection<Long> categoryIds,
		String keyword, List<String> brands, Long minPrice, Long maxPrice, Set<Certification> certifications,
		boolean certifiedOnly, boolean excludeSoldOut) {

	/** 카테고리와 검색어만 거는 조건. 필터 항목별 개수를 셀 때 쓴다. */
	public static ProductSearchCondition of(Collection<ProductStatus> statuses, Collection<Long> categoryIds,
			String keyword) {
		return new ProductSearchCondition(statuses, categoryIds, keyword, List.of(), null, null, Set.of(), false,
				false);
	}

	public boolean hasPriceRange() {
		return minPrice != null || maxPrice != null;
	}
}
