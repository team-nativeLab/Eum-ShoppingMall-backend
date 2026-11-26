package com.example.shopping.domain.member.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 보낸 항목만 수정한다. 연락처는 빈 문자열을 보내면 지운다. 이메일(로그인 식별자)은 바꿀 수 없다.
 *
 * @param name  이름(50자 이하). 생략하면 그대로
 * @param phone 연락처(예: 010-1234-5678, 하이픈은 생략 가능). 빈 문자열이면 지우고, 생략하면 그대로
 */
public record ProfileUpdateRequest(
		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		String name,

		@Pattern(regexp = ValidationPatterns.PHONE_OR_EMPTY, message = "연락처 형식이 올바르지 않습니다.")
		String phone) {
}
