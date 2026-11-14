package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.ProductReview;

/**
 * 상품 상세의 리뷰. 작성자 이름은 가려서 준다(홍*동). 관리자 메모는 넣지 않는다.
 *
 * @param optionLabel 주문할 때 고른 옵션 이름
 * @param isMine      로그인한 회원 본인이 쓴 리뷰인지
 */
public record ProductReviewResponse(
		Long id,
		String authorName,
		int rating,
		String content,
		String optionLabel,
		LocalDateTime createdAt,
		String adminReply,
		LocalDateTime adminRepliedAt,
		boolean isMine) {

	public static ProductReviewResponse of(ProductReview review, String maskedAuthorName, Long viewerId) {
		return new ProductReviewResponse(review.getId(), maskedAuthorName, review.getRating(), review.getContent(),
				review.getOptionLabel(), review.getCreatedAt(), review.getAdminReply(), review.getAdminRepliedAt(),
				viewerId != null && viewerId.equals(review.getMemberId()));
	}
}
