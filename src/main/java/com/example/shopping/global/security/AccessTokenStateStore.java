package com.example.shopping.global.security;

import java.time.Duration;

/** Stores short-lived access-token revocation state shared by application instances. */
public interface AccessTokenStateStore {

	long versionForIssuing(Long memberId);

	long currentVersion(Long memberId);

	long incrementVersion(Long memberId);

	boolean isRevoked(String tokenId);

	void revoke(String tokenId, Duration ttl);
}
