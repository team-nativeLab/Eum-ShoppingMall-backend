package com.example.shopping.domain.member.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 처음 소셜 로그인한 사람의 가입 대기(#225). 제공자가 확인해 준 정보를 잠깐 두었다가, 약관 동의를 받아 가입을 끝낼 때 한 번만 쓴다.
 * 토큰 원문은 저장하지 않는다.
 */
@Entity
@Table(name = "social_signup_ticket")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialSignupTicket {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	@Column(nullable = false, length = 100)
	private String providerUserId;

	@Column(nullable = false, length = 255)
	private String email;

	// 제공자가 인증한 이메일인지. 가입하면 이메일 인증을 마친 회원이 된다.
	@Column(nullable = false)
	private boolean emailVerified;

	// 제공자가 알려 준 이름(닉네임). 가입 화면의 기본값
	@Column(length = 50)
	private String name;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	public static SocialSignupTicket of(String tokenHash, SocialProvider provider, String providerUserId, String email,
			boolean emailVerified, String name, LocalDateTime expiresAt) {
		SocialSignupTicket ticket = new SocialSignupTicket();
		ticket.tokenHash = tokenHash;
		ticket.provider = provider;
		ticket.providerUserId = providerUserId;
		ticket.email = email;
		ticket.emailVerified = emailVerified;
		ticket.name = name;
		ticket.expiresAt = expiresAt;
		return ticket;
	}

	public boolean isExpired(LocalDateTime now) {
		return !expiresAt.isAfter(now);
	}
}
