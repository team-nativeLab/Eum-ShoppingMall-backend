package com.example.shopping.global.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** Rejects access tokens after logout, password changes, account deactivation, or role changes. */
@Component
@RequiredArgsConstructor
public class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token", "Access token is no longer valid", null);

	private final MemberRepository memberRepository;
	private final AccessTokenStateStore stateStore;

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		try {
			Long memberId = Long.valueOf(jwt.getSubject());
			Member member = memberRepository.findById(memberId).orElse(null);
			if (member == null || member.getStatus() != MemberStatus.ACTIVE
					|| !member.getRole().name().equals(jwt.getClaimAsString(JwtTokenProvider.ROLE_CLAIM))) {
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
			if (issuedVersion != stateStore.currentVersion(memberId)) {
				return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
			}
			return OAuth2TokenValidatorResult.success();
		} catch (IllegalArgumentException exception) {
			return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
		}
	}
}
