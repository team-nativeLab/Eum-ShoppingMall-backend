package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.ProductReview;

/**
 * 상품 상세의 리뷰. 작성자 이름은 가려서 준다(홍*동). 관리자 메모는 넣지 않는다.
 *
 * @param id             리뷰 ID
 * @param authorName     작성자 이름(가림, 예: 홍*동)
 * @param rating         별점(1~5)
 * @param content        리뷰 내용
 * @param optionLabel    주문할 때 고른 옵션 이름
 * @param createdAt      작성 시각
 * @param adminReply     관리자 답글. 없으면 null
 * @param adminRepliedAt 답글 시각. 없으면 null
 * @param isMine         로그인한 회원 본인이 쓴 리뷰인지
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
