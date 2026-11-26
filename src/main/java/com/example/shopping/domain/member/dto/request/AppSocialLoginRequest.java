package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 앱 카카오·구글 로그인(#225).
 *
 * @param token 카카오는 카카오 SDK가 준 액세스 토큰, 구글은 Google Sign-In이 준 ID 토큰
 */
public record AppSocialLoginRequest(
		@NotBlank(message = "카카오·구글 토큰을 보내 주세요.")
		@Size(max = 4096, message = "토큰이 너무 깁니다.")
		String token) {

	/** 토큰 원문은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "AppSocialLoginRequest[token=****]";
	}
}
