package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.product.entity.ProductQuestion;
import com.example.shopping.domain.product.entity.ProductQuestionStatus;

/**
 * 상품 상세에 보여 줄 상품 문의. 작성자 이름은 가려서 준다(홍*동).
 * 비밀 문의는 작성자 본인이 아니면 제목을 "비밀 문의입니다"로 바꾸고 내용과 답변을 비운다.
 *
 * @param id         문의 ID
 * @param authorName 작성자 이름(가림, 예: 홍*동)
 * @param title      제목. 남의 비밀 문의면 "비밀 문의입니다."
 * @param content    문의 내용. 남의 비밀 문의면 null
 * @param isPrivate  비밀 문의인지
 * @param status     답변 상태. WAITING(답변 대기), ANSWERED(답변 완료)
 * @param createdAt  작성 시각
 * @param answer     답변. 답변 전이거나 남의 비밀 문의면 null
 * @param answeredAt 답변 시각. 답변 전이면 null
 * @param isMine     로그인한 회원 본인이 쓴 문의인지
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
