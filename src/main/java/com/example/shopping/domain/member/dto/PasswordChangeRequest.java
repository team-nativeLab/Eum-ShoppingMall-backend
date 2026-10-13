package com.example.shopping.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
		@NotBlank(message = "현재 비밀번호를 입력해 주세요.")
		@Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.")
		String currentPassword,

		// 회원가입과 같은 규칙
		@NotBlank(message = "새 비밀번호를 입력해 주세요.")
		@Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "비밀번호는 공백 없이 영문·숫자·특수문자 8~64자여야 합니다.")
		String newPassword) {

	/** 요청 객체가 로그에 찍히더라도 비밀번호는 남지 않게 한다. */
	@Override
	public String toString() {
		return "PasswordChangeRequest[currentPassword=****, newPassword=****]";
	}
}
