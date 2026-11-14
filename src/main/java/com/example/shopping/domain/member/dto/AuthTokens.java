package com.example.shopping.domain.member.dto;

/**
 * 로그인·재발급 결과. Access 토큰은 응답 본문으로, Refresh 토큰은 HttpOnly 쿠키로 내려준다.
 *
 * @param expiresIn             Access 토큰 수명(초)
 * @param refreshTokenExpiresIn Refresh 토큰을 쓸 수 있는 남은 시간(초). 쿠키 Max-Age로 쓴다.
 */
public record AuthTokens(String accessToken, long expiresIn, String refreshToken, long refreshTokenExpiresIn) {
}
