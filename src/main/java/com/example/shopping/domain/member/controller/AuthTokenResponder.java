package com.example.shopping.domain.member.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.domain.member.dto.response.LoginResponse;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.security.JwtProperties;

import lombok.RequiredArgsConstructor;

/**
 * 토큰을 HTTP 응답으로 옮긴다. Refresh 토큰은 스크립트가 읽을 수 없는 HttpOnly 쿠키에 담고,
 * 다른 사이트에서 보낸 요청에는 실리지 않도록 SameSite=Strict, 재발급·로그아웃 경로로만 가도록 Path를 제한한다.
 * 관리자 세션은 쿠키 이름과 경로를 따로 써서 고객 사이트의 재발급 요청에 실리지 않는다(#163).
 */
@Component
@RequiredArgsConstructor
public class AuthTokenResponder {

	public static final String REFRESH_COOKIE = "refresh_token";
	public static final String ADMIN_REFRESH_COOKIE = "admin_refresh_token";

	private final JwtProperties jwtProperties;

	/** 쿠키 이름과, 쿠키를 보낼 재발급·로그아웃 API의 경로 */
	public enum Session {
		MEMBER(REFRESH_COOKIE, "/api/v1/auth"),
		ADMIN(ADMIN_REFRESH_COOKIE, "/api/v1/admin/auth");

		private final String cookieName;
		private final String cookiePath;

		Session(String cookieName, String cookiePath) {
			this.cookieName = cookieName;
			this.cookiePath = cookiePath;
		}
	}

	public ResponseEntity<ApiResponse<LoginResponse>> withTokens(Session session, AuthTokens tokens) {
		return withTokens(session, tokens, new LoginResponse(tokens.accessToken(), tokens.expiresIn()));
	}

	/** 응답 본문을 따로 정할 때. 쿠키 Max-Age는 Refresh 토큰을 쓸 수 있는 남은 시간이다. */
	public <T> ResponseEntity<ApiResponse<T>> withTokens(Session session, AuthTokens tokens, T body) {
		ResponseCookie cookie = cookie(session, tokens.refreshToken(), Duration.ofSeconds(tokens.refreshTokenExpiresIn()));
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(ApiResponse.ok(body));
	}

	public ResponseEntity<ApiResponse<Void>> withoutTokens(Session session) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie(session, "", Duration.ZERO).toString())
				.body(ApiResponse.ok(null));
	}

	private ResponseCookie cookie(Session session, String value, Duration maxAge) {
		return ResponseCookie.from(session.cookieName, value)
				.httpOnly(true)
				.secure(jwtProperties.refreshCookieSecure())
				.sameSite(jwtProperties.refreshCookieSameSite())
				.path(session.cookiePath)
				.maxAge(maxAge)
				.build();
	}
}
