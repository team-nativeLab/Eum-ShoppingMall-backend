package com.example.shopping.global.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

	public static final String ROLE_CLAIM = "role";
	public static final String VERSION_CLAIM = "token_version";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;

	/** sub에는 회원 ID, role에는 권한 이름(USER, ADMIN)을 담는다. */
	public String createAccessToken(Long memberId, String role, long tokenVersion) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(memberId))
				.id(UUID.randomUUID().toString())
				.claim(ROLE_CLAIM, role)
				.claim(VERSION_CLAIM, tokenVersion)
				.issuedAt(now)
				.expiresAt(now.plusSeconds(properties.accessTokenExpiry()))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public long accessTokenExpiry() {
		return properties.accessTokenExpiry();
	}
}
