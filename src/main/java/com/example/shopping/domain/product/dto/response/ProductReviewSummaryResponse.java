package com.example.shopping.domain.product.dto.response;

import java.util.Map;

/**
 * 상품 리뷰 요약
 *
 * @param averageRating    보이는 리뷰의 평균 별점(소수 첫째 자리). 리뷰가 없으면 null
 * @param ratingCounts     별점(1~5)별 리뷰 수. 리뷰가 없는 별점도 0으로 넣는다.
 * @param photoReviewCount 사진 리뷰 수. 사진 리뷰는 아직 받지 않아 0이다.
 */
public record ProductReviewSummaryResponse(
		Long productId,
		Double averageRating,
		long totalCount,
		Map<Integer, Long> ratingCounts,
		long photoReviewCount) {
}
