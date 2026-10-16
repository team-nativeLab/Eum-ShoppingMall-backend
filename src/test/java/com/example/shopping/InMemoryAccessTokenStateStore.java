package com.example.shopping;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.shopping.global.security.AccessTokenStateStore;
import com.example.shopping.global.security.JwtProperties;

import lombok.RequiredArgsConstructor;

@Component
@Profile("test")
@RequiredArgsConstructor
class InMemoryAccessTokenStateStore implements AccessTokenStateStore {

	private record Value(long value, Instant expiresAt) {
	}

	private final ConcurrentMap<String, Value> versions = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Instant> revokedTokens = new ConcurrentHashMap<>();
	private final JwtProperties properties;

	@Override
	public long versionForIssuing(Long memberId) {
		String key = String.valueOf(memberId);
		Value current = liveVersion(key);
		if (current == null) {
			return 0;
		}
		versions.put(key, new Value(current.value(), versionExpiry()));
		return current.value();
	}

	@Override
	public long currentVersion(Long memberId) {
		Value current = liveVersion(String.valueOf(memberId));
		return current == null ? 0 : current.value();
	}

	@Override
	public long incrementVersion(Long memberId) {
		String key = String.valueOf(memberId);
		Value current = liveVersion(key);
		long version = (current == null ? 0 : current.value()) + 1;
		versions.put(key, new Value(version, versionExpiry()));
		return version;
	}

	@Override
	public boolean isRevoked(String tokenId) {
		Instant expiresAt = revokedTokens.get(tokenId);
		if (expiresAt == null) {
			return false;
		}
		if (!expiresAt.isAfter(Instant.now())) {
			revokedTokens.remove(tokenId, expiresAt);
			return false;
		}
		return true;
	}

	@Override
	public void revoke(String tokenId, Duration ttl) {
		if (!ttl.isNegative() && !ttl.isZero()) {
			revokedTokens.put(tokenId, Instant.now().plus(ttl));
		}
	}

	private Value liveVersion(String key) {
		Value current = versions.get(key);
		if (current != null && !current.expiresAt().isAfter(Instant.now())) {
			versions.remove(key, current);
			return null;
		}
		return current;
	}

	private Instant versionExpiry() {
		return Instant.now().plusSeconds(properties.accessTokenExpiry() + 60);
	}
}
