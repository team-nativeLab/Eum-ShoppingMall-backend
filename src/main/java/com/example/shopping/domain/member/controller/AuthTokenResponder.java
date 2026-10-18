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
 */
@Component
@RequiredArgsConstructor
public class AuthTokenResponder {

	public static final String REFRESH_COOKIE = "refresh_token";
	private static final String COOKIE_PATH = "/api/v1/auth";

	private final JwtProperties jwtProperties;

	public ResponseEntity<ApiResponse<LoginResponse>> withTokens(AuthTokens tokens) {
		ResponseCookie cookie = cookie(tokens.refreshToken(), Duration.ofSeconds(jwtProperties.refreshTokenExpiry()));
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(ApiResponse.ok(new LoginResponse(tokens.accessToken(), tokens.expiresIn())));
	}

	public ResponseEntity<ApiResponse<Void>> withoutTokens() {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
				.body(ApiResponse.ok(null));
	}

	private ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
				.httpOnly(true)
				.secure(jwtProperties.refreshCookieSecure())
				.sameSite(jwtProperties.refreshCookieSameSite())
				.path(COOKIE_PATH)
				.maxAge(maxAge)
				.build();
	}
}
