package com.example.shopping.domain.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.example.shopping.domain.member.entity.RefreshToken;
import com.example.shopping.domain.member.repository.RefreshTokenRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.security.JwtProperties;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretHashes;

import lombok.extern.slf4j.Slf4j;

/**
 * Refresh 토큰 발급·교체·폐기. 토큰은 재발급할 때마다 새것으로 바뀌고(rotation) 쓴 토큰은 다시 쓸 수 없다.
 * 실패해도 폐기 처리는 남아야 하므로 메서드를 트랜잭션으로 묶지 않고 리포지토리 호출 단위로 반영한다.
 */
@Slf4j
@Service
public class RefreshTokenService {

	// 프론트가 동시에 보낸 재발급 요청 중 늦은 쪽을 탈취로 오인하지 않기 위한 여유 시간
	private static final int REUSE_GRACE_SECONDS = 10;
	private static final Duration USED_TOKEN_REPLAY_RETENTION = Duration.ofDays(1);
	private static final int PURGE_BATCH_SIZE = 500;

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProperties jwtProperties;
	private final KeyedHash tokenHashes;
	private final SecureRandom secureRandom = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties,
			SecretHashes secretHashes) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtProperties = jwtProperties;
		this.tokenHashes = secretHashes.forPurpose(SecretHashes.REFRESH_TOKEN);
	}

	/** 회원 세션. 쓸 때마다 새 토큰으로 바뀌고, 마지막 재발급부터 14일 동안 쓸 수 있다. @return 클라이언트에 내려줄 토큰 원문 */
	public String issue(Long memberId, long tokenVersion) {
		String token = newToken();
		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpiry());
		refreshTokenRepository.save(RefreshToken.create(memberId, hash(token), expiresAt, tokenVersion));
		return token;
	}

	/** 회원 Refresh 토큰의 수명(초). 쿠키 Max-Age로 쓴다. */
	public long memberTokenLifetimeSeconds() {
		return jwtProperties.refreshTokenExpiry();
	}

	/**
	 * 관리자 세션(#164). idleTimeout 동안 재발급하지 않으면 끝나고, 계속 쓰더라도 sessionExpiresAt에 끝난다.
	 * 「개인정보의 안전성 확보조치 기준」 제6조의 접속 자동 차단이다.
	 *
	 * @param sessionExpiresAt 로그인할 때 정한 세션 종료 시각. 재발급할 때는 전 토큰의 값을 그대로 넘긴다.
	 */
	public IssuedToken issueAdmin(Long memberId, long tokenVersion, LocalDateTime sessionExpiresAt,
			Duration idleTimeout) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime idleExpiresAt = now.plus(idleTimeout);
		LocalDateTime expiresAt = idleExpiresAt.isBefore(sessionExpiresAt) ? idleExpiresAt : sessionExpiresAt;
		if (!expiresAt.isAfter(now)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		String token = newToken();
		refreshTokenRepository.save(RefreshToken.create(memberId, hash(token), expiresAt, tokenVersion,
				sessionExpiresAt));
		return new IssuedToken(token, Math.max(1, Duration.between(now, expiresAt).toSeconds()));
	}

	private String newToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** @param expiresIn 토큰을 쓸 수 있는 남은 시간(초). 쿠키 Max-Age로 쓴다. */
	public record IssuedToken(String token, long expiresIn) {
	}

	/**
	 * 토큰을 사용 처리하고 주인의 회원 ID를 돌려준다. 쓸 수 없는 토큰이면 401.
	 * 이미 사용된 토큰이 다시 들어오면 탈취된 것으로 보고 그 회원의 Refresh 토큰을 전부 폐기한다.
	 */
	public RefreshToken consume(String token) {
		if (token == null || token.isBlank()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(token))
				.or(() -> refreshTokenRepository.findByTokenHash(legacyHash(token)))
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
		return stored;
	}

	public void revoke(String token) {
		if (token != null && !token.isBlank()) {
			refreshTokenRepository.deleteByTokenHash(hash(token));
			refreshTokenRepository.deleteByTokenHash(legacyHash(token));
		}
	}

	/** 비밀번호 변경 등으로 모든 기기를 로그아웃시킬 때 */
	public void revokeAll(Long memberId) {
		refreshTokenRepository.deleteAllByMemberId(memberId);
	}

	public int purgeExpired() {
		LocalDateTime now = LocalDateTime.now();
		List<Long> expiredIds = refreshTokenRepository.findExpiredIds(now, PageRequest.of(0, PURGE_BATCH_SIZE));
		int deleted = deleteBatch(expiredIds);

		List<Long> usedIds = refreshTokenRepository.findUsedIdsBefore(now.minus(USED_TOKEN_REPLAY_RETENTION),
				PageRequest.of(0, PURGE_BATCH_SIZE));
		return deleted + deleteBatch(usedIds);
	}

	private int deleteBatch(List<Long> tokenIds) {
		return tokenIds.isEmpty() ? 0 : refreshTokenRepository.deleteBatchByIds(tokenIds);
	}

	/** 서버 키로 만든 해시(#136). DB가 새도 토큰 원문을 넣어 보며 맞힐 수 없다. */
	private String hash(String token) {
		return tokenHashes.hash(token);
	}

	/**
	 * 서버 키 없이 SHA-256으로 저장하던 이전 토큰. 배포 전에 받은 토큰으로도 한 번 더 재발급받을 수 있게 함께 찾는다.
	 * 재발급하면 새 방식으로 바뀌므로, 배포하고 Refresh 토큰 유효 기간(14일)이 지나면 지워도 된다.
	 */
	private static String legacyHash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
