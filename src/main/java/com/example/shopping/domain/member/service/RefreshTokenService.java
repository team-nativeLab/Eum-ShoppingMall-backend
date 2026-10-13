package com.example.shopping.domain.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.example.shopping.domain.member.entity.RefreshToken;
import com.example.shopping.domain.member.repository.RefreshTokenRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.security.JwtProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Refresh 토큰 발급·교체·폐기. 토큰은 재발급할 때마다 새것으로 바뀌고(rotation) 쓴 토큰은 다시 쓸 수 없다.
 * 실패해도 폐기 처리는 남아야 하므로 메서드를 트랜잭션으로 묶지 않고 리포지토리 호출 단위로 반영한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	// 프론트가 동시에 보낸 재발급 요청 중 늦은 쪽을 탈취로 오인하지 않기 위한 여유 시간
	private static final int REUSE_GRACE_SECONDS = 10;

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProperties jwtProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	/** @return 클라이언트에 내려줄 토큰 원문 */
	public String issue(Long memberId) {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpiry());
		refreshTokenRepository.save(RefreshToken.create(memberId, hash(token), expiresAt));
		return token;
	}

	/**
	 * 토큰을 사용 처리하고 주인의 회원 ID를 돌려준다. 쓸 수 없는 토큰이면 401.
	 * 이미 사용된 토큰이 다시 들어오면 탈취된 것으로 보고 그 회원의 Refresh 토큰을 전부 폐기한다.
	 */
	public Long consume(String token) {
		if (token == null || token.isBlank()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(token))
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		LocalDateTime now = LocalDateTime.now();

		if (stored.getUsedAt() != null) {
			if (stored.getUsedAt().isBefore(now.minusSeconds(REUSE_GRACE_SECONDS))) {
				log.warn("사용된 Refresh 토큰 재사용 감지. 회원 {}의 토큰을 모두 폐기합니다.", stored.getMemberId());
				refreshTokenRepository.deleteAllByMemberId(stored.getMemberId());
			}
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		if (stored.getExpiresAt().isBefore(now) || refreshTokenRepository.markUsed(stored.getId(), now) == 0) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return stored.getMemberId();
	}

	public void revoke(String token) {
		if (token != null && !token.isBlank()) {
			refreshTokenRepository.deleteByTokenHash(hash(token));
		}
	}

	/** 비밀번호 변경 등으로 모든 기기를 로그아웃시킬 때 */
	public void revokeAll(Long memberId) {
		refreshTokenRepository.deleteAllByMemberId(memberId);
	}

	public int purgeExpired() {
		return refreshTokenRepository.deleteExpired(LocalDateTime.now());
	}

	private String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
