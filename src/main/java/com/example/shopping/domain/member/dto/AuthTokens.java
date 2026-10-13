package com.example.shopping.domain.member.dto;

/** 로그인·재발급 결과. Access 토큰은 응답 본문으로, Refresh 토큰은 HttpOnly 쿠키로 내려준다. */
public record AuthTokens(String accessToken, long expiresIn, String refreshToken) {
}
