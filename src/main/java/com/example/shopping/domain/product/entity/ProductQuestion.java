package com.example.shopping.domain.product.entity;

import java.time.LocalDateTime;

import org.springframework.util.StringUtils;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 상세의 상품 문의(Q&A). 회원이 남기고 관리자가 답한다.
 * 비밀 문의는 작성자 본인과 관리자만 내용과 답변을 본다. 관리자 메모는 고객에게 보이지 않는다.
 */
@Entity
@Table(name = "product_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductQuestion extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "private_question", nullable = false)
	private boolean privateQuestion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductQuestionStatus status = ProductQuestionStatus.WAITING;

	@Column(columnDefinition = "TEXT")
	private String answer;

	private LocalDateTime answeredAt;

	// 답변한 관리자
	private Long answeredBy;

	// 고객에게 보이지 않는 관리자 메모
	@Column(columnDefinition = "TEXT")
	private String adminMemo;

	public static ProductQuestion create(Product product, Long memberId, String title, String content,
			boolean privateQuestion) {
		ProductQuestion question = new ProductQuestion();
		question.product = product;
		question.memberId = memberId;
		question.title = title;
		question.content = content;
		question.privateQuestion = privateQuestion;
		return question;
	}

	/** 답변을 남기면 답변 완료, 빈 답변을 남기면 답변을 지우고 답변 대기로 되돌린다. */
	public void answer(String answer, Long adminId, LocalDateTime now) {
		if (StringUtils.hasText(answer)) {
			this.answer = answer;
			this.answeredAt = now;
			this.answeredBy = adminId;
			this.status = ProductQuestionStatus.ANSWERED;
		} else {
			this.answer = null;
			this.answeredAt = null;
			this.answeredBy = null;
			this.status = ProductQuestionStatus.WAITING;
		}
	}

	public void changeAdminMemo(String adminMemo) {
		this.adminMemo = StringUtils.hasText(adminMemo) ? adminMemo : null;
	}
}
