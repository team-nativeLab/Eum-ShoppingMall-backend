package com.example.shopping.domain.member.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Pattern(regexp = ValidationPatterns.ASCII_EMAIL, message = "이메일은 영문·숫자·기호로만 입력해 주세요.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.")
		String password) {

	/** 요청 객체가 로그에 찍히더라도 비밀번호는 남지 않게 한다. */
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=****]";
	}
}
