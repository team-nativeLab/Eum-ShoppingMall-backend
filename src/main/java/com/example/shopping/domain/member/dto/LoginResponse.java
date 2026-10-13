package com.example.shopping.domain.member.dto;

/** expiresIn은 Access 토큰의 남은 수명(초). 토큰은 Authorization: Bearer 헤더로 보낸다. */
public record LoginResponse(String accessToken, long expiresIn) {
}
