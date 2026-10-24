package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		// BCrypt는 72바이트까지만 처리하므로 1바이트 문자(영문·숫자·특수문자)로 제한한다.
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "비밀번호는 공백 없이 영문·숫자·특수문자 8~64자여야 합니다.")
		String password,

		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		String name,

		// 선택 입력
		@Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
		String phone,

		// 이메일 인증(POST /api/v1/auth/email-verifications/confirm)으로 받은 토큰.
		// SIGNUP_EMAIL_VERIFICATION_REQUIRED=true면 필수다.
		@Size(max = 100, message = "이메일 인증 토큰이 올바르지 않습니다.")
		String emailVerificationToken) {

	/** 요청 객체가 로그에 찍히더라도 비밀번호는 남지 않게 한다. */
	@Override
	public String toString() {
		return "SignupRequest[email=" + email + ", password=****, name=" + name + ", phone=" + phone
				+ ", emailVerificationToken=" + (emailVerificationToken == null ? null : "****") + "]";
	}
}
