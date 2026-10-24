package com.example.shopping.domain.product.dto.response;

import java.util.List;

/**
 * 검색 화면의 카테고리 탭과 필터에 보여 줄 항목별 상품 수. 모두 상품 수가 많은 순이다.
 *
 * @param totalCount        검색어와 카테고리 조건에 맞는 상품 수
 * @param keywordTotalCount 검색어 조건만 적용한 상품 수 (카테고리 탭의 "전체")
 * @param categories        최상위 카테고리별 상품 수. 검색어 조건만 적용하고, 하위 카테고리의 상품은 최상위로 합쳐 센다.
 * @param brands            브랜드별 상품 수 (검색어와 카테고리 조건 적용)
 * @param certifications    인증 종류별 상품 수 (검색어와 카테고리 조건 적용)
 */
public record ProductFacetsResponse(long totalCount, long keywordTotalCount, List<FacetItem<Long>> categories,
		List<FacetItem<String>> brands, List<FacetItem<String>> certifications) {

	/**
	 * @param value 필터 요청에 그대로 쓰는 값 (카테고리 ID, 브랜드 이름, 인증 코드)
	 * @param label 화면에 보여 줄 이름
	 * @param count 상품 수
	 */
	public record FacetItem<T>(T value, String label, long count) {
	}
}
