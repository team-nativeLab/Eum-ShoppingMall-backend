package com.example.shopping.domain.support.entity;

import java.time.LocalDateTime;

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

	// 개인정보 수집·이용 동의 시각과 그때의 개인정보 처리방침 버전. 동의 없이 접수한 회원 문의는 null이다.
	private LocalDateTime privacyAgreedAt;

	@Column(length = 40)
	private String privacyPolicyVersion;

	// 보유 기간이 지나 연락처·내용을 지운 시각(#134). PersonalDataRetentionCleaner만 채운다. 동의 기록은 남긴다.
	private LocalDateTime personalDataPurgedAt;

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

	public void recordPrivacyConsent(LocalDateTime agreedAt, String policyVersion) {
		this.privacyAgreedAt = agreedAt;
		this.privacyPolicyVersion = policyVersion;
	}

	/** 답변을 등록(또는 수정)하고 답변완료로 바꾼다. */
	public void answer(String answer) {
		this.answer = answer;
		this.status = InquiryStatus.ANSWERED;
	}
}
