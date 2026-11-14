package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.product.entity.ProductReview;

/**
 * 관리자 리뷰. 작성자는 회원 ID·이름으로 보여 준다(개인정보 조회로 감사 로그에 남는다).
 *
 * @param authorName 작성자 이름. 탈퇴했으면 "탈퇴한 회원"
 */
public record AdminProductReviewResponse(
		Long id,
		Long productId,
		String productName,
		Long memberId,
		String authorName,
		Long orderItemId,
		int rating,
		String content,
		String optionLabel,
		boolean visible,
		LocalDateTime createdAt,
		String adminReply,
		LocalDateTime adminRepliedAt,
		String adminMemo) {

	public static AdminProductReviewResponse of(ProductReview review, Member author) {
		return new AdminProductReviewResponse(review.getId(), review.getProduct().getId(),
				review.getProduct().getName(), review.getMemberId(), author == null ? null : author.getName(),
				review.getOrderItemId(), review.getRating(), review.getContent(), review.getOptionLabel(),
				review.isVisible(), review.getCreatedAt(), review.getAdminReply(), review.getAdminRepliedAt(),
				review.getAdminMemo());
	}
}
