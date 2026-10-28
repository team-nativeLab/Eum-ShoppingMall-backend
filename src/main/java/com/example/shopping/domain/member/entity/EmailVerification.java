package com.example.shopping.domain.member.entity;

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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이메일로 보낸 인증번호. 인증번호와 회원가입용 확인 토큰은 해시로만 저장한다.
 * 회원가입은 인증번호 확인 → 확인 토큰 발급 → 가입 요청에 토큰 제출 순서로, 비밀번호 재설정은 인증번호와 새 비밀번호를 함께 보내 쓴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "email_verification", indexes = {
		@Index(name = "ix_email_verification_email_purpose", columnList = "email, purpose"),
		@Index(name = "ix_email_verification_token_hash", columnList = "token_hash"),
		@Index(name = "ix_email_verification_created_at", columnList = "created_at") })
public class EmailVerification extends BaseTimeEntity {

	/** 인증번호를 이 횟수만큼 틀리면 다시 받아야 한다. */
	public static final int MAX_ATTEMPTS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private VerificationPurpose purpose;

	@Column(nullable = false, length = 64)
	private String codeHash;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	/** 회원가입 인증을 마치면 발급하는 확인 토큰의 해시 */
	@Column(name = "token_hash", length = 64)
	private String tokenHash;

	private LocalDateTime tokenExpiresAt;

	/** 가입·비밀번호 재설정에 썼거나, 새 인증번호를 받아 더 쓸 수 없게 된 시각 */
	private LocalDateTime consumedAt;

	public static EmailVerification create(String email, VerificationPurpose purpose, String codeHash,
			LocalDateTime expiresAt) {
		EmailVerification verification = new EmailVerification();
		verification.email = email;
		verification.purpose = purpose;
		verification.codeHash = codeHash;
		verification.expiresAt = expiresAt;
		return verification;
	}

	public boolean isCodeUsable(LocalDateTime now) {
		return consumedAt == null && expiresAt.isAfter(now) && attempts < MAX_ATTEMPTS;
	}

	public boolean isAttemptsExceeded() {
		return attempts >= MAX_ATTEMPTS;
	}

	public void recordFailedAttempt() {
		attempts++;
	}

	public void issueToken(String tokenHash, LocalDateTime tokenExpiresAt) {
		this.tokenHash = tokenHash;
		this.tokenExpiresAt = tokenExpiresAt;
	}

	public boolean isTokenUsable(LocalDateTime now) {
		return consumedAt == null && tokenExpiresAt != null && tokenExpiresAt.isAfter(now);
	}

	public void consume(LocalDateTime now) {
		this.consumedAt = now;
	}
}
