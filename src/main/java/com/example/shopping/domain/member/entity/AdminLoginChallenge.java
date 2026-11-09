package com.example.shopping.domain.member.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비밀번호를 확인한 관리자가 OTP를 넣을 때까지 쓰는 1회용 토큰(#164). 토큰 원문이 아니라 서버 키로 만든 해시만 저장한다.
 * 5분이 지나거나, 5번 틀리거나, 비밀번호·권한이 바뀌면(토큰 버전) 쓸 수 없다.
 */
@Entity
@Table(indexes = { @Index(columnList = "member_id"), @Index(columnList = "expires_at") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminLoginChallenge {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private long tokenVersion;

	@ColumnDefault("0")
	@Column(nullable = false)
	private int failedAttempts;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	public static AdminLoginChallenge create(Long memberId, String tokenHash, long tokenVersion,
			LocalDateTime expiresAt) {
		AdminLoginChallenge challenge = new AdminLoginChallenge();
		challenge.memberId = memberId;
		challenge.tokenHash = tokenHash;
		challenge.tokenVersion = tokenVersion;
		challenge.expiresAt = expiresAt;
		return challenge;
	}
}
