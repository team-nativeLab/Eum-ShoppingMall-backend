package com.example.shopping.domain.quote.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 견적 문의. 금액 미표시 상품의 구매 경로로도 쓰인다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteInquiry extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 비회원 접수를 허용하게 되면 null
	private Long memberId;

	// 특정 상품에 대한 문의가 아니면 null
	private Long productId;

	@Column(nullable = false, length = 100)
	private String companyName;

	@Column(nullable = false, length = 50)
	private String managerName;

	@Column(nullable = false, length = 30)
	private String phone;

	@Column(length = 255)
	private String email;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private QuoteStatus status = QuoteStatus.RECEIVED;

	/** memberId는 비회원이면 null, productId는 특정 상품에 대한 문의가 아니면 null */
	public static QuoteInquiry create(Long memberId, Long productId, String companyName, String managerName,
			String phone, String email, String content) {
		QuoteInquiry quote = new QuoteInquiry();
		quote.memberId = memberId;
		quote.productId = productId;
		quote.companyName = companyName;
		quote.managerName = managerName;
		quote.phone = phone;
		quote.email = email;
		quote.content = content;
		return quote;
	}

	public void changeStatus(QuoteStatus status) {
		this.status = status;
	}
}
