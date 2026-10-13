package com.example.shopping.domain.member.dto;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 항목만 수정한다. 연락처는 빈 문자열을 보내면 지운다. 이메일(로그인 식별자)은 바꿀 수 없다. */
public record ProfileUpdateRequest(
		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		String name,

		@Pattern(regexp = "^$|^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
		String phone) {
}
