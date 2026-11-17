package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 앱 토큰 재발급·로그아웃 요청(#203).
 *
 * @param refreshToken 앱 로그인·재발급 응답으로 받은 Refresh 토큰
 */
public record AppRefreshRequest(
		@NotBlank(message = "Refresh 토큰을 보내 주세요.")
		@Size(max = 512, message = "Refresh 토큰 형식이 올바르지 않습니다.")
		String refreshToken) {

	/** 토큰 원문이 로그에 남지 않게 가린다. */
	@Override
	public String toString() {
		return "AppRefreshRequest[refreshToken=****]";
	}
}
