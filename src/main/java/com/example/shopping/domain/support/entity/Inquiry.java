package com.example.shopping.domain.support.entity;

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

/** 1:1 문의 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 비회원 문의는 null. 이 경우 답변은 아래 연락처로 전달한다.
	private Long memberId;

	@Column(length = 30)
	private String contactPhone;

	@Column(length = 255)
	private String contactEmail;

	@Column(nullable = false, length = 50)
	private String inquiryType;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(columnDefinition = "TEXT")
	private String answer;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private InquiryStatus status = InquiryStatus.RECEIVED;

	/** memberId가 null이면 비회원 문의 */
	public static Inquiry create(Long memberId, String inquiryType, String title, String content,
			String contactPhone, String contactEmail) {
		Inquiry inquiry = new Inquiry();
		inquiry.memberId = memberId;
		inquiry.inquiryType = inquiryType;
		inquiry.title = title;
		inquiry.content = content;
		inquiry.contactPhone = contactPhone;
		inquiry.contactEmail = contactEmail;
		return inquiry;
	}

	/** 답변을 등록(또는 수정)하고 답변완료로 바꾼다. */
	public void answer(String answer) {
		this.answer = answer;
		this.status = InquiryStatus.ANSWERED;
	}
}
