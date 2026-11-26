package com.example.shopping.domain.member.dto.response;

import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.domain.member.service.SocialLoginResult;

/**
 * 앱 카카오·구글 로그인 응답(#225). 로그인했으면 앱 로그인처럼 Access·Refresh 토큰을 모두 본문으로 준다.
 * 처음 온 사람이면 signupRequired가 true이고, signupToken으로 가입을 끝낸다(POST /api/v1/app/auth/social/signup).
 */
public record AppSocialLoginResponse(boolean signupRequired, String accessToken, Long expiresIn,
		String refreshToken, Long refreshTokenExpiresIn, String signupToken, Long signupTokenExpiresIn,
		SocialProvider provider, String email, String name) {

	public static AppSocialLoginResponse loggedIn(AuthTokens tokens) {
		return new AppSocialLoginResponse(false, tokens.accessToken(), tokens.expiresIn(), tokens.refreshToken(),
				tokens.refreshTokenExpiresIn(), null, null, null, null, null);
	}

	public static AppSocialLoginResponse signupRequired(SocialLoginResult.SignupPending signup) {
		return new AppSocialLoginResponse(true, null, null, null, null, signup.token(), signup.expiresIn(),
				signup.provider(), signup.email(), signup.name());
	}

	/** 토큰 원문은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "AppSocialLoginResponse[signupRequired=" + signupRequired + ", accessToken="
				+ (accessToken == null ? null : "****") + ", refreshToken=" + (refreshToken == null ? null : "****")
				+ ", signupToken=" + (signupToken == null ? null : "****") + ", provider=" + provider + "]";
	}
}
