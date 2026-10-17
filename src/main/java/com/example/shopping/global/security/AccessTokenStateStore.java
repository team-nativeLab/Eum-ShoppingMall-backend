package com.example.shopping.global.security;

import java.time.Duration;

/** Stores short-lived access-token revocation state shared by application instances. */
public interface AccessTokenStateStore {

	boolean isRevoked(String tokenId);

	void revoke(String tokenId, Duration ttl);
}
