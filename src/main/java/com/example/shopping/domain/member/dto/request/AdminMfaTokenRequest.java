package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param mfaToken 관리자 로그인 1단계가 준 토큰 */
public record AdminMfaTokenRequest(
		@NotBlank(message = "로그인을 먼저 해 주세요.")
		@Size(max = 100, message = "로그인 확인 값이 올바르지 않습니다.")
		String mfaToken) {
}
