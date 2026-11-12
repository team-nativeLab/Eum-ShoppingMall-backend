package com.example.shopping.domain.product.entity;

import java.time.LocalDateTime;

import org.springframework.util.StringUtils;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 상품 리뷰. 배송이 끝난 주문의 상품마다(주문 품목 하나에) 하나씩 쓴다.
 * 관리자가 숨기면 목록과 평점에서 빠진다. 관리자 답변은 고객에게 보이고, 관리자 메모는 보이지 않는다.
 */
@Entity
@Table(name = "product_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReview extends BaseTimeEntity {

	public static final int MIN_RATING = 1;
	public static final int MAX_RATING = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	// 리뷰를 쓴 주문 품목. 품목마다 리뷰는 하나다.
	@Column(name = "order_item_id", nullable = false, unique = true)
	private Long orderItemId;

	@Column(nullable = false)
	private int rating;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	// 주문할 때 고른 옵션 이름(스냅샷)
	@Column(length = 100)
	private String optionLabel;

	@Column(nullable = false)
	private boolean visible = true;

	@Column(columnDefinition = "TEXT")
	private String adminReply;

	private LocalDateTime adminRepliedAt;

	@Column(columnDefinition = "TEXT")
	private String adminMemo;

	public static ProductReview create(Product product, Long memberId, Long orderItemId, int rating, String content,
			String optionLabel) {
		if (rating < MIN_RATING || rating > MAX_RATING) {
			throw new IllegalArgumentException("별점은 1~5여야 합니다.");
		}
		ProductReview review = new ProductReview();
		review.product = product;
		review.memberId = memberId;
		review.orderItemId = orderItemId;
		review.rating = rating;
		review.content = content;
		review.optionLabel = optionLabel;
		return review;
	}

	/** @return 보이는 상태가 바뀌었는지 */
	public boolean changeVisible(boolean visible) {
		if (this.visible == visible) {
			return false;
		}
		this.visible = visible;
		return true;
	}

	/** 빈 답변이면 답변을 지운다. */
	public void reply(String reply, LocalDateTime now) {
		if (StringUtils.hasText(reply)) {
			this.adminReply = reply;
			this.adminRepliedAt = now;
		} else {
			this.adminReply = null;
			this.adminRepliedAt = null;
		}
	}

	public void changeAdminMemo(String adminMemo) {
		this.adminMemo = StringUtils.hasText(adminMemo) ? adminMemo : null;
	}
}
