package com.example.shopping.domain.member.dto.response;

import com.example.shopping.domain.member.dto.AuthTokens;

/**
 * 앱 로그인·재발급 응답(#203). 웹과 달리 Refresh 토큰도 본문으로 준다(쿠키 없음).
 * 앱은 Refresh 토큰을 기기 보안 저장소(iOS Keychain, Android Keystore)에 두고, 로그·화면·일반 저장소에 남기지 않는다.
 *
 * @param expiresIn             Access 토큰을 쓸 수 있는 시간(초)
 * @param refreshToken          재발급·로그아웃에 쓰는 토큰. 재발급할 때마다 새것으로 바뀌고, 한 번 쓴 토큰은 다시 쓸 수 없다.
 * @param refreshTokenExpiresIn Refresh 토큰을 쓸 수 있는 시간(초). 마지막 재발급부터 센다.
 */
public record AppTokenResponse(String accessToken, long expiresIn, String refreshToken, long refreshTokenExpiresIn) {

	public static AppTokenResponse from(AuthTokens tokens) {
		return new AppTokenResponse(tokens.accessToken(), tokens.expiresIn(), tokens.refreshToken(),
				tokens.refreshTokenExpiresIn());
	}

	/** 토큰 원문이 로그에 남지 않게 가린다. */
	@Override
	public String toString() {
		return "AppTokenResponse[accessToken=****, expiresIn=" + expiresIn + ", refreshToken=****, refreshTokenExpiresIn="
				+ refreshTokenExpiresIn + "]";
	}
}
