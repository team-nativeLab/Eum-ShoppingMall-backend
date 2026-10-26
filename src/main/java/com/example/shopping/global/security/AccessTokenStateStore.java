package com.example.shopping.global.security;

import java.time.Duration;

/** Stores short-lived access-token revocation state shared by application instances. */
public interface AccessTokenStateStore {

	/** @throws AccessTokenStateUnavailableException when the shared store cannot answer (e.g. Redis outage) */
	boolean isRevoked(String tokenId);

	void revoke(String tokenId, Duration ttl);

	/** 공유 저장소(Redis)에 장애가 나 차단 여부를 확인할 수 없을 때 */
	class AccessTokenStateUnavailableException extends RuntimeException {

		public AccessTokenStateUnavailableException(Throwable cause) {
			super("Access token revocation state is unavailable", cause);
		}
	}
}
