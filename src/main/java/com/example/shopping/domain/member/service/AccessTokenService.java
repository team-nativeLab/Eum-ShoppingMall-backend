package com.example.shopping.domain.member.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import com.example.shopping.global.security.AccessTokenStateStore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccessTokenService {

	private static final long CLOCK_SKEW_SECONDS = 60;

	private final JwtDecoder jwtDecoder;
	private final AccessTokenStateStore stateStore;

	/** Revokes a valid bearer token. Expired or malformed tokens need no revocation. */
	public void revokeIfValid(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return;
		}
		String token = authorizationHeader.substring("Bearer ".length()).trim();
		if (token.isEmpty()) {
			return;
		}
		try {
			Jwt jwt = jwtDecoder.decode(token);
			if (jwt.getId() == null || jwt.getExpiresAt() == null) {
				return;
			}
			Duration ttl = Duration.between(Instant.now(), jwt.getExpiresAt()).plusSeconds(CLOCK_SKEW_SECONDS);
			stateStore.revoke(jwt.getId(), ttl);
		} catch (JwtException ignored) {
			// Logout must still revoke the refresh cookie when the access token has expired or is malformed.
		}
	}
}
