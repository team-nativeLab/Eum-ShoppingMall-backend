package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원가입 이메일 인증번호 요청, 비밀번호 재설정 인증번호 요청에 함께 쓴다. */
public record EmailVerificationSendRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email) {
}
