package com.example.shopping.global.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.global.security.MemberTokenStateCache.MemberTokenState;

import lombok.RequiredArgsConstructor;

/**
 * Rejects access tokens after logout, password changes, or account deactivation.
 * Member state comes from MemberTokenStateCache, so a request does not query the member table each time.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token", "Access token is no longer valid", null);

	private final MemberTokenStateCache memberTokenStateCache;
	private final AccessTokenStateStore stateStore;

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		try {
			Long memberId = Long.valueOf(jwt.getSubject());
			String roleClaim = jwt.getClaimAsString(JwtTokenProvider.ROLE_CLAIM);
			if (roleClaim == null) {
				return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
			}
			MemberRole.valueOf(roleClaim);
			MemberTokenState member = memberTokenStateCache.get(memberId).orElse(null);
			if (member == null || member.status() != MemberStatus.ACTIVE) {
				return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
			}

			String tokenId = jwt.getId();
			if (tokenId != null && stateStore.isRevoked(tokenId)) {
				return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
			}

			Object versionClaim = jwt.getClaims().get(JwtTokenProvider.VERSION_CLAIM);
			if (versionClaim != null && !(versionClaim instanceof Number)) {
				return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
			}
			long issuedVersion = versionClaim == null ? 0 : ((Number) versionClaim).longValue();
			if (issuedVersion != member.accessTokenVersion()) {
				return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
			}
			return OAuth2TokenValidatorResult.success();
		} catch (IllegalArgumentException exception) {
			return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
		}
	}
}
