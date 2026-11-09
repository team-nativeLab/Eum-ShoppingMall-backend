package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param mfaToken 관리자 로그인 1단계가 준 토큰
 * @param code     OTP 앱의 6자리 숫자
 */
public record AdminMfaVerifyRequest(
		@NotBlank(message = "로그인을 먼저 해 주세요.")
		@Size(max = 100, message = "로그인 확인 값이 올바르지 않습니다.")
		String mfaToken,
		@NotBlank(message = "인증 코드를 입력해 주세요.")
		@Pattern(regexp = "[0-9]{6}", message = "인증 코드는 숫자 6자리입니다.")
		String code) {
}
