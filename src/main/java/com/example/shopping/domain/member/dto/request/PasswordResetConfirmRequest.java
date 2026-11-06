package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param captchaToken 로봇 확인 위젯이 준 토큰. 인증번호를 여러 번 틀려 CAPTCHA_REQUIRED를 받은 뒤부터 보낸다.
 */
public record PasswordResetConfirmRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		@NotBlank(message = "인증번호를 입력해 주세요.")
		@Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "인증번호는 영문 대소문자와 숫자 6자리입니다.")
		String code,

		// 회원가입과 같은 규칙
		@NotBlank(message = "새 비밀번호를 입력해 주세요.")
		@Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "비밀번호는 공백 없이 영문·숫자·특수문자 8~64자여야 합니다.")
		String newPassword,

		@Size(max = 2048, message = "로봇 확인 값이 올바르지 않습니다.")
		String captchaToken) {

	/** 요청 객체가 로그에 찍히더라도 비밀번호·인증번호·로봇 확인 토큰은 남지 않게 한다. */
	@Override
	public String toString() {
		return "PasswordResetConfirmRequest[email=" + email + ", code=****, newPassword=****, captchaToken="
				+ (captchaToken == null ? null : "****") + "]";
	}
}
