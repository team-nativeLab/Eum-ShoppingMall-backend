package com.example.shopping.domain.member.dto.response;

import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.domain.member.service.SocialLoginResult;

/**
 * 웹 카카오·구글 로그인 응답(#225). 로그인했으면 accessToken·expiresIn이 있고 Refresh 토큰은 쿠키로 받는다(이메일 로그인과 같다).
 * 처음 온 사람이면 signupRequired가 true이고, signupToken으로 가입을 끝낸다(POST /api/v1/auth/social/signup).
 *
 * @param signupRequired       처음 온 사람이라 가입을 끝내야 하는지
 * @param accessToken          Access 토큰. 가입이 필요하면 null
 * @param expiresIn            Access 토큰을 쓸 수 있는 시간(초). 가입이 필요하면 null
 * @param signupToken          가입에 쓰는 토큰(10분, 1회). 로그인했으면 null
 * @param signupTokenExpiresIn 가입을 끝낼 수 있는 남은 시간(초)
 * @param provider             카카오·구글 중 어느 쪽인지(KAKAO, GOOGLE). 로그인했으면 null
 * @param email                가입할 이메일(카카오·구글이 알려 준 값)
 * @param name                 가입 화면 이름 칸의 기본값. 없으면 null
 */
public record SocialLoginResponse(boolean signupRequired, String accessToken, Long expiresIn, String signupToken,
		Long signupTokenExpiresIn, SocialProvider provider, String email, String name) {

	public static SocialLoginResponse loggedIn(String accessToken, long expiresIn) {
		return new SocialLoginResponse(false, accessToken, expiresIn, null, null, null, null, null);
	}

	public static SocialLoginResponse signupRequired(SocialLoginResult.SignupPending signup) {
		return new SocialLoginResponse(true, null, null, signup.token(), signup.expiresIn(), signup.provider(),
				signup.email(), signup.name());
	}

	/** 토큰 원문은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "SocialLoginResponse[signupRequired=" + signupRequired + ", accessToken="
				+ (accessToken == null ? null : "****") + ", signupToken=" + (signupToken == null ? null : "****")
				+ ", provider=" + provider + "]";
	}
}
