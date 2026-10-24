package com.example.shopping.global.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.shopping.global.redis.RedisCircuitBreaker;

/**
 * 로그아웃한 Access 토큰을 Redis에 남겨 모든 서버가 거절하게 한다.
 * 이 서버에서 차단한 토큰은 메모리에도 남겨, Redis에 장애가 나도 이 서버만큼은 계속 거절한다.
 */
@Component
@Profile("!test")
public class RedisAccessTokenStateStore implements AccessTokenStateStore {

	private static final Logger log = LoggerFactory.getLogger(RedisAccessTokenStateStore.class);
	private static final String REVOKED_KEY_PREFIX = "auth:revoked-access-token:";
	private static final int MAX_LOCAL_REVOCATIONS = 10_000;

	private final StringRedisTemplate redis;
	private final RedisCircuitBreaker redisCircuitBreaker;
	private final ConcurrentMap<String, Instant> localRevocations = new ConcurrentHashMap<>();

	public RedisAccessTokenStateStore(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker) {
		this.redis = redis;
		this.redisCircuitBreaker = redisCircuitBreaker;
	}

	@Override
	public boolean isRevoked(String tokenId) {
		Instant locallyRevokedUntil = localRevocations.get(tokenId);
		if (locallyRevokedUntil != null) {
			if (locallyRevokedUntil.isAfter(Instant.now())) {
				return true;
			}
			localRevocations.remove(tokenId, locallyRevokedUntil);
		}
		try {
			return Boolean.TRUE.equals(redisCircuitBreaker.execute(() -> redis.hasKey(REVOKED_KEY_PREFIX + tokenId)));
		} catch (DataAccessException ex) {
			throw new AccessTokenStateUnavailableException(ex);
		}
	}

	/** Redis에 남기지 못해도 로그아웃은 실패시키지 않는다. 이 서버의 메모리에는 남는다. */
	@Override
	public void revoke(String tokenId, Duration ttl) {
		if (ttl.isNegative() || ttl.isZero()) {
			return;
		}
		rememberLocally(tokenId, ttl);
		try {
			redisCircuitBreaker.run(() -> redis.opsForValue().set(REVOKED_KEY_PREFIX + tokenId, "1", ttl));
		} catch (DataAccessException ex) {
			log.warn("Could not store access-token revocation in Redis; only this instance will reject the token", ex);
		}
	}

	private void rememberLocally(String tokenId, Duration ttl) {
		if (localRevocations.size() >= MAX_LOCAL_REVOCATIONS) {
			Instant now = Instant.now();
			localRevocations.values().removeIf(until -> !until.isAfter(now));
		}
		if (localRevocations.size() < MAX_LOCAL_REVOCATIONS) {
			localRevocations.put(tokenId, Instant.now().plus(ttl));
		}
	}
}
