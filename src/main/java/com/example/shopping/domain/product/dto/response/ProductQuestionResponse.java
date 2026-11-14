package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.ProductQuestion;
import com.example.shopping.domain.product.entity.ProductQuestionStatus;

/**
 * 상품 상세에 보여 줄 상품 문의. 작성자 이름은 가려서 준다(홍*동).
 * 비밀 문의는 작성자 본인이 아니면 제목을 "비밀 문의입니다"로 바꾸고 내용과 답변을 비운다.
 *
 * @param isMine 로그인한 회원 본인이 쓴 문의인지
 */
public record ProductQuestionResponse(
		Long id,
		String authorName,
		String title,
		String content,
		boolean isPrivate,
		ProductQuestionStatus status,
		LocalDateTime createdAt,
		String answer,
		LocalDateTime answeredAt,
		boolean isMine) {

	public static final String HIDDEN_TITLE = "비밀 문의입니다.";

	public static ProductQuestionResponse of(ProductQuestion question, String maskedAuthorName, Long viewerId) {
		boolean mine = viewerId != null && viewerId.equals(question.getMemberId());
		boolean hidden = question.isPrivateQuestion() && !mine;
		return new ProductQuestionResponse(question.getId(), maskedAuthorName,
				hidden ? HIDDEN_TITLE : question.getTitle(),
				hidden ? null : question.getContent(),
				question.isPrivateQuestion(), question.getStatus(), question.getCreatedAt(),
				hidden ? null : question.getAnswer(),
				question.getAnsweredAt(), mine);
	}
}
