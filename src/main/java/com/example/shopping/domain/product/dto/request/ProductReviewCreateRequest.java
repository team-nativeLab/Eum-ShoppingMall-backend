package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 리뷰 작성
 *
 * @param orderItemId 리뷰를 쓸 주문 품목. GET /api/v1/me/reviewable-items에서 고른다.
 * @param rating      별점 1~5
 */
public record ProductReviewCreateRequest(
		@NotNull(message = "리뷰를 쓸 주문 상품을 골라 주세요.")
		Long orderItemId,

		@NotNull(message = "별점을 골라 주세요.")
		@Min(value = 1, message = "별점은 1~5점입니다.")
		@Max(value = 5, message = "별점은 1~5점입니다.")
		Integer rating,

		@NotBlank(message = "리뷰 내용을 입력해 주세요.")
		@Size(max = 2000, message = "리뷰는 2,000자 이하여야 합니다.")
		String content) {
}
