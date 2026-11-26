package com.example.shopping.domain.member.service;

import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.entity.SocialProvider;

/**
 * 소셜 로그인 결과(#225). 연결된(또는 자동으로 연결한) 회원이면 tokens, 처음 온 사람이면 signup이 있다.
 */
public record SocialLoginResult(AuthTokens tokens, SignupPending signup) {

	static SocialLoginResult loggedIn(AuthTokens tokens) {
		return new SocialLoginResult(tokens, null);
	}

	static SocialLoginResult signupRequired(SocialSignupTickets.Issued ticket, SocialProvider provider, String email,
			String name) {
		return new SocialLoginResult(null, new SignupPending(ticket.token(), ticket.expiresIn(), provider, email, name));
	}

	public boolean signupRequired() {
		return signup != null;
	}

	/**
	 * @param token     가입을 끝낼 때 보내는 1회용 토큰
	 * @param expiresIn 가입을 끝낼 수 있는 남은 시간(초)
	 * @param email     제공자가 알려 준 이메일. 가입하면 이 이메일로 가입한다.
	 * @param name      제공자가 알려 준 이름(닉네임). 가입 화면의 기본값. 없으면 null
	 */
	public record SignupPending(String token, long expiresIn, SocialProvider provider, String email, String name) {
	}
}
