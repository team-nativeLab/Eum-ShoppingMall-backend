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
	/** SecurityErrorHandler turns this into 503 instead of 401, so clients retry instead of logging out. */
	static final OAuth2Error STATE_UNAVAILABLE = new OAuth2Error("temporarily_unavailable",
			"Access token state is temporarily unavailable", null);

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
			MemberRole role = MemberRole.valueOf(roleClaim);
			MemberTokenState member = memberTokenStateCache.get(memberId).orElse(null);
			if (member == null || member.status() != MemberStatus.ACTIVE) {
				return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
			}

			String tokenId = jwt.getId();
			if (tokenId != null) {
				try {
					if (stateStore.isRevoked(tokenId)) {
						return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
					}
				} catch (AccessTokenStateStore.AccessTokenStateUnavailableException unavailable) {
					// 로그아웃 여부를 확인할 수 없을 때: 관리자 토큰은 받지 않고(503), 일반 회원은 아래의 계정 상태·토큰 버전
					// 확인만으로 계속 처리한다. 비밀번호 변경·정지·탈퇴는 토큰 버전으로 막히고, 다른 서버에서 로그아웃한 토큰만 놓친다.
					if (role.canAccessAdminApi()) {
						return OAuth2TokenValidatorResult.failure(STATE_UNAVAILABLE);
					}
				}
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
