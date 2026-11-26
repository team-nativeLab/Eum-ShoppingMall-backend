package com.example.shopping.domain.member.dto.response;

/**
 * expiresIn은 Access 토큰의 남은 수명(초). 토큰은 Authorization: Bearer 헤더로 보낸다.
 *
 * @param accessToken Access 토큰. Authorization: Bearer 헤더로 보낸다.
 * @param expiresIn   Access 토큰을 쓸 수 있는 시간(초)
 */
public record LoginResponse(String accessToken, long expiresIn) {
}
