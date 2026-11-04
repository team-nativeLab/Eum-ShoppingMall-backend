package com.example.shopping.domain.consent.entity;

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

/**
 * 회원의 동의 기록. 동의한 시각은 created_at이다.
 * 분쟁 때 근거가 되므로 고치지 않고, 동의를 바꾸면 새 행을 남긴다. 탈퇴해도 지우지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberConsent extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ConsentType consentType;

	@Column(nullable = false)
	private boolean agreed;

	// 동의할 때 보여 준 문서의 버전. 문서가 없는 항목(마케팅 수신)이나 아직 등록되지 않은 문서는 null이다.
	@Column(length = 40)
	private String documentVersion;

	public static MemberConsent of(Long memberId, ConsentType consentType, boolean agreed, String documentVersion) {
		MemberConsent consent = new MemberConsent();
		consent.memberId = memberId;
		consent.consentType = consentType;
		consent.agreed = agreed;
		consent.documentVersion = documentVersion;
		return consent;
	}
}
