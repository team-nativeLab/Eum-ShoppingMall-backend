package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.history.dto.StatusHistoryResponse;
import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.product.entity.ProductQuestion;
import com.example.shopping.domain.product.entity.ProductQuestionStatus;

/**
 * 관리자 상품 문의. 비밀 문의도 내용을 모두 보여 주고, 작성자는 회원 ID·이름으로 보여 준다(개인정보 조회로 감사 로그에 남는다).
 *
 * @param authorName    작성자 이름. 탈퇴했으면 "탈퇴한 회원"
 * @param statusHistory 상태가 바뀐 기록. 오래된 것부터(#223). 목록에서는 싣지 않는다(null).
 */
public record AdminProductQuestionResponse(
		Long id,
		Long productId,
		String productName,
		Long memberId,
		String authorName,
		String title,
		String content,
		boolean isPrivate,
		ProductQuestionStatus status,
		LocalDateTime createdAt,
		String answer,
		LocalDateTime answeredAt,
		Long answeredBy,
		String adminMemo,
		List<StatusHistoryResponse> statusHistory) {

	public static AdminProductQuestionResponse of(ProductQuestion question, Member author) {
		return of(question, author, null);
	}

	public static AdminProductQuestionResponse of(ProductQuestion question, Member author,
			List<StatusHistoryResponse> statusHistory) {
		return new AdminProductQuestionResponse(question.getId(), question.getProduct().getId(),
				question.getProduct().getName(), question.getMemberId(), author == null ? null : author.getName(),
				question.getTitle(), question.getContent(), question.isPrivateQuestion(), question.getStatus(),
				question.getCreatedAt(), question.getAnswer(), question.getAnsweredAt(), question.getAnsweredBy(),
				question.getAdminMemo(), statusHistory);
	}
}
