package com.example.shopping.global.validation;

import java.util.regex.Pattern;

/**
 * 평문으로만 받는 관리자 글(고정 콘텐츠, 약관 문서). 화면은 텍스트로만 그리지만,
 * 서식 태그를 넣으면 태그가 글자 그대로 노출되므로 저장할 때 거절한다.
 */
public final class PlainText {

	private static final Pattern HTML_TAG = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");

	private PlainText() {
	}

	public static boolean containsHtmlTag(String text) {
		return text != null && HTML_TAG.matcher(text).find();
	}
}
