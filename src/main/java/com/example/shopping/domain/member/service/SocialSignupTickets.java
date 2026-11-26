package com.example.shopping.domain.member.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.example.shopping.domain.member.entity.SocialSignupTicket;
import com.example.shopping.domain.member.repository.SocialSignupTicketRepository;
import com.example.shopping.domain.member.social.SocialProfile;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretHashes;

/**
 * 처음 소셜 로그인한 사람의 가입 대기(#225). 제공자가 확인해 준 정보를 10분 동안 두고, 가입을 끝낼 때 한 번만 쓴다.
 * 토큰 원문은 응답으로만 주고, 저장소에는 용도별 키의 HMAC만 둔다.
 */
@Component
public class SocialSignupTickets {

	static final Duration LIFETIME = Duration.ofMinutes(10);

	private final SocialSignupTicketRepository ticketRepository;
	private final KeyedHash tokenHashes;
	private final SecureRandom secureRandom = new SecureRandom();

	public SocialSignupTickets(SocialSignupTicketRepository ticketRepository, SecretHashes secretHashes) {
		this.ticketRepository = ticketRepository;
		this.tokenHashes = secretHashes.forPurpose(SecretHashes.SOCIAL_SIGNUP_TICKET);
	}

	/** @param email 로그인 이메일로 맞춘 값 */
	public Issued issue(SocialProfile profile, String email) {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		ticketRepository.save(SocialSignupTicket.of(tokenHashes.hash(token), profile.provider(),
				profile.providerUserId(), email, profile.emailVerified(), name(profile.name()),
				LocalDateTime.now().plus(LIFETIME)));
		return new Issued(token, LIFETIME.toSeconds());
	}

	/** 한 번만 쓴다. 없거나, 이미 썼거나, 10분이 지났으면 400(SOCIAL_SIGNUP_TOKEN_INVALID) */
	public SocialSignupTicket consume(String token) {
		SocialSignupTicket ticket = ticketRepository.findByTokenHash(tokenHashes.hash(token))
				.orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID));
		if (ticketRepository.deleteOnce(ticket.getId()) != 1 || ticket.isExpired(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.SOCIAL_SIGNUP_TOKEN_INVALID);
		}
		return ticket;
	}

	/** 회원 이름 칸(50자)에 맞춘다. */
	private static String name(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
	}

	/** @param expiresIn 가입을 끝낼 수 있는 남은 시간(초) */
	public record Issued(String token, long expiresIn) {
	}
}
