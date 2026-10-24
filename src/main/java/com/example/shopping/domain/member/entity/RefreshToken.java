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

/** Refresh 토큰. DB가 유출돼도 토큰을 쓸 수 없도록 원문이 아니라 SHA-256 해시만 저장한다. */
@Entity
@Table(indexes = @Index(columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	@ColumnDefault("0")
	@Column(name = "token_version", nullable = false)
	private long tokenVersion;

	// 재발급에 사용된 시각. 한 번 쓴 토큰은 다시 쓸 수 없다. 아직 쓰지 않았으면 null
	private LocalDateTime usedAt;

	public static RefreshToken create(Long memberId, String tokenHash, LocalDateTime expiresAt, long tokenVersion) {
		RefreshToken token = new RefreshToken();
		token.memberId = memberId;
		token.tokenHash = tokenHash;
		token.expiresAt = expiresAt;
		token.tokenVersion = tokenVersion;
		return token;
	}
}
