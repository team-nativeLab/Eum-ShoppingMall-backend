package com.example.shopping.global.security;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisAccessTokenStateStore implements AccessTokenStateStore {

	private static final String REVOKED_KEY_PREFIX = "auth:revoked-access-token:";

	private final StringRedisTemplate redis;

	@Override
	public boolean isRevoked(String tokenId) {
		return Boolean.TRUE.equals(redis.hasKey(REVOKED_KEY_PREFIX + tokenId));
	}

	@Override
	public void revoke(String tokenId, Duration ttl) {
		if (!ttl.isNegative() && !ttl.isZero()) {
			redis.opsForValue().set(REVOKED_KEY_PREFIX + tokenId, "1", ttl);
		}
	}
}
