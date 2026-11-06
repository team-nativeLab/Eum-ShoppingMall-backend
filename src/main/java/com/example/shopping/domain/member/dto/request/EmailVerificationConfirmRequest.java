package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param captchaToken 로봇 확인 위젯이 준 토큰. 인증번호를 여러 번 틀려 CAPTCHA_REQUIRED를 받은 뒤부터 보낸다.
 */
public record EmailVerificationConfirmRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		@NotBlank(message = "인증번호를 입력해 주세요.")
		@Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "인증번호는 영문 대소문자와 숫자 6자리입니다.")
		String code,

		@Size(max = 2048, message = "로봇 확인 값이 올바르지 않습니다.")
		String captchaToken) {

	/** 요청 객체가 로그에 찍히더라도 인증번호와 로봇 확인 토큰은 남지 않게 한다. */
	@Override
	public String toString() {
		return "EmailVerificationConfirmRequest[email=" + email + ", code=****, captchaToken="
				+ (captchaToken == null ? null : "****") + "]";
	}
}
