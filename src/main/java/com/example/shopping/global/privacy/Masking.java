package com.example.shopping.global.privacy;

/** 다른 사람에게 보여 주는 화면에서 개인 식별 정보를 가린다. */
public final class Masking {

	private Masking() {
	}

	/** 이름의 첫 글자와 마지막 글자만 남긴다. 홍길동 → 홍*동, 남궁민수 → 남**수, 홍길 → 홍*, 홍 → * */
	public static String name(String name) {
		if (name == null || name.isBlank()) {
			return "*";
		}
		String trimmed = name.strip();
		int length = trimmed.codePointCount(0, trimmed.length());
		if (length == 1) {
			return "*";
		}
		String first = new String(Character.toChars(trimmed.codePointAt(0)));
		if (length == 2) {
			return first + "*";
		}
		String last = new String(Character.toChars(trimmed.codePointBefore(trimmed.length())));
		return first + "*".repeat(length - 2) + last;
	}
}
