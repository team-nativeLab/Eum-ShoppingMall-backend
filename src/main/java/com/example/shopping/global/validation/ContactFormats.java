package com.example.shopping.global.validation;

import java.util.Locale;

/**
 * 연락처와 로그인 이메일을 저장 형식으로 맞춘다. 같은 값이 입력 화면마다 다르게 저장되지 않도록 모든 입력에서 이것만 쓴다.
 * 형식 검사는 ValidationPatterns.PHONE을 쓴다.
 */
public final class ContactFormats {

	private ContactFormats() {
	}

	/** 하이픈을 빼 숫자만 남긴다(예: 010-1234-5678 → 01012345678). null은 null, 빈 문자열은 빈 문자열로 둔다. */
	public static String phone(String value) {
		return value == null ? null : value.replace("-", "").trim();
	}

	/** 로그인 식별자로 쓰는 이메일. 앞뒤 공백을 빼고 소문자로 맞춘다. */
	public static String loginEmail(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}
}
