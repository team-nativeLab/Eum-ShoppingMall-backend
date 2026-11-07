package com.example.shopping.global.persistence;

/**
 * 사용자가 입력한 검색어로 LIKE 패턴을 만든다. 검색어에 든 %와 _는 "아무 글자"가 아니라 글자 그대로 찾는다.
 * 만든 패턴은 반드시 {@code like :pattern escape '\'}(Criteria API는 {@link #ESCAPE})와 함께 쓴다.
 */
public final class LikePatterns {

	/** 패턴의 이스케이프 문자 */
	public static final char ESCAPE = '\\';

	private LikePatterns() {
	}

	/** 검색어를 포함하는 값 */
	public static String contains(String keyword) {
		return "%" + escape(keyword) + "%";
	}

	/** 검색어로 시작하는 값 */
	public static String prefix(String keyword) {
		return escape(keyword) + "%";
	}

	private static String escape(String keyword) {
		return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
