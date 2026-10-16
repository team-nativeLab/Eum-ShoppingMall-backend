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

	private static final String VERSION_KEY_PREFIX = "auth:access-token-version:";
	private static final String REVOKED_KEY_PREFIX = "auth:revoked-access-token:";
	private static final long CLOCK_SKEW_SECONDS = 60;

	private final StringRedisTemplate redis;
	private final JwtProperties properties;

	@Override
	public long versionForIssuing(Long memberId) {
		String key = versionKey(memberId);
		String value = redis.opsForValue().get(key);
		if (value == null) {
			return 0;
		}
		redis.expire(key, versionTtl());
		return Long.parseLong(value);
	}

	@Override
	public long currentVersion(Long memberId) {
		String value = redis.opsForValue().get(versionKey(memberId));
		return value == null ? 0 : Long.parseLong(value);
	}

	@Override
	public long incrementVersion(Long memberId) {
		Long version = redis.opsForValue().increment(versionKey(memberId));
		redis.expire(versionKey(memberId), versionTtl());
		return version == null ? 0 : version;
	}

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

	private String versionKey(Long memberId) {
		return VERSION_KEY_PREFIX + memberId;
	}

	private Duration versionTtl() {
		return Duration.ofSeconds(properties.accessTokenExpiry() + CLOCK_SKEW_SECONDS);
	}
}
