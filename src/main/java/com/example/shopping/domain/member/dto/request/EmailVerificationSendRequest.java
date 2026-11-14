package com.example.shopping.domain.member.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 이메일 인증번호 요청, 비밀번호 재설정 인증번호 요청에 함께 쓴다.
 *
 * @param captchaToken 로봇 확인 위젯(action=send-code)이 준 토큰. 하루에 3통을 넘게 요청해 CAPTCHA_REQUIRED를 받은 뒤부터 보낸다.
 */
public record EmailVerificationSendRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Pattern(regexp = ValidationPatterns.ASCII_EMAIL, message = "이메일은 영문·숫자·기호로만 입력해 주세요.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,
		@Size(max = 2048, message = "로봇 확인 값이 올바르지 않습니다.")
		String captchaToken) {
}
