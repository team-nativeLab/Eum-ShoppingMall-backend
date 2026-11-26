package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.product.entity.ProductReview;

/**
 * 관리자 리뷰. 작성자는 회원 ID·이름으로 보여 준다(개인정보 조회로 감사 로그에 남는다).
 *
 * @param id             리뷰 ID
 * @param productId      상품 ID
 * @param productName    상품명
 * @param memberId       작성자 회원 ID
 * @param authorName     작성자 이름. 탈퇴했으면 "탈퇴한 회원"
 * @param orderItemId    리뷰를 쓴 주문 품목 ID
 * @param rating         별점(1~5)
 * @param content        리뷰 내용
 * @param optionLabel    주문할 때 고른 옵션 이름
 * @param visible        고객 화면에 보이는지
 * @param createdAt      작성 시각
 * @param adminReply     관리자 답글. 없으면 null
 * @param adminRepliedAt 답글 시각. 없으면 null
 * @param adminMemo      고객에게 보이지 않는 내부 메모(#223)
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
