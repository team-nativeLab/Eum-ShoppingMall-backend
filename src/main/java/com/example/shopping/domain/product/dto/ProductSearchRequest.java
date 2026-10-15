package com.example.shopping.domain.product.dto;

import java.util.List;

import com.example.shopping.domain.product.entity.Certification;

/**
 * 고객용 상품 목록·검색 조건 (쿼리 파라미터). 모두 선택이며 함께 주면 모두 만족하는 상품만 나온다.
 *
 * @param categoryId     이 카테고리와 그 하위 카테고리의 상품만
 * @param query          상품명 또는 브랜드에 포함된 글자
 * @param brand          이 중 하나의 브랜드인 상품만 (?brand=A&brand=B)
 * @param minPrice       가격 하한(원). 가격대를 지정하면 금액 미표시 상품은 결과에서 빠진다.
 * @param maxPrice       가격 상한(원)
 * @param certified      true면 어떤 인증이든 하나 이상 받은 상품만
 * @param certification  이 중 하나라도 받은 상품만 (?certification=KCS&certification=TYPE_APPROVAL)
 * @param excludeSoldOut true면 품절 상품을 뺀다
 * @param sort           recommended(기본), newest, price_asc, price_desc, popular
 */
public record ProductSearchRequest(Long categoryId, String query, List<String> brand, Long minPrice, Long maxPrice,
		Boolean certified, List<Certification> certification, Boolean excludeSoldOut, String sort) {
}
