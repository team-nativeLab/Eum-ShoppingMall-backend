package com.example.shopping;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.shopping.global.security.AccessTokenStateStore;

@Component
@Profile("test")
class InMemoryAccessTokenStateStore implements AccessTokenStateStore {
	private final java.util.concurrent.ConcurrentMap<String, java.time.Instant> revokedTokens =
			new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public boolean isRevoked(String tokenId) {
		java.time.Instant expiresAt = revokedTokens.get(tokenId);
		if (expiresAt == null) {
			return false;
		}
		if (!expiresAt.isAfter(java.time.Instant.now())) {
			revokedTokens.remove(tokenId, expiresAt);
			return false;
		}
		return true;
	}

	@Override
	public void revoke(String tokenId, Duration ttl) {
		if (!ttl.isNegative() && !ttl.isZero()) {
			revokedTokens.put(tokenId, java.time.Instant.now().plus(ttl));
		}
	}
}
