package com.example.shopping.domain.member.dto.response;

import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.domain.member.service.SocialLoginResult;

/**
 * 앱 카카오·구글 로그인 응답(#225). 로그인했으면 앱 로그인처럼 Access·Refresh 토큰을 모두 본문으로 준다.
 * 처음 온 사람이면 signupRequired가 true이고, signupToken으로 가입을 끝낸다(POST /api/v1/app/auth/social/signup).
 *
 * @param signupRequired        처음 온 사람이라 가입을 끝내야 하는지
 * @param accessToken           Access 토큰. 가입이 필요하면 null
 * @param expiresIn             Access 토큰을 쓸 수 있는 시간(초). 가입이 필요하면 null
 * @param refreshToken          Refresh 토큰. 기기 보안 저장소에 둔다. 가입이 필요하면 null
 * @param refreshTokenExpiresIn Refresh 토큰을 쓸 수 있는 시간(초). 가입이 필요하면 null
 * @param signupToken           가입에 쓰는 토큰(10분, 1회). 로그인했으면 null
 * @param signupTokenExpiresIn  가입을 끝낼 수 있는 남은 시간(초). 로그인했으면 null
 * @param provider              카카오·구글 중 어느 쪽인지(KAKAO, GOOGLE). 로그인했으면 null
 * @param email                 가입할 이메일(카카오·구글이 알려 준 값). 로그인했으면 null
 * @param name                  가입 화면 이름 칸의 기본값. 로그인했거나 알 수 없으면 null
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
