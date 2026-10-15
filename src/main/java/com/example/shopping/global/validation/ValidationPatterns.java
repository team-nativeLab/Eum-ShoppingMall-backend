package com.example.shopping.global.validation;

/** 요청 DTO의 @Pattern에서 함께 쓰는 정규식 */
public final class ValidationPatterns {

	/** 값이 있다면 공백만으로 이뤄져서는 안 된다. 수정(PATCH) 요청의 선택 입력 문자열에 쓴다. */
	public static final String HAS_TEXT = "(?s).*\\S.*";

	/** http(s) 주소 또는 /로 시작하는 서버 내 경로. javascript: 같은 스킴을 막는다. */
	public static final String URL_OR_PATH = "^(https?://|/)\\S+$";

	/** URL_OR_PATH와 같지만 빈 문자열도 허용한다. 수정 요청에서 빈 문자열은 값을 지운다는 뜻이다. */
	public static final String URL_OR_PATH_OR_EMPTY = "^$|" + URL_OR_PATH;

	/** 주소에 쓰는 영문 이름. 영문 소문자·숫자를 하이픈으로 이은 형태 (예: head-protection) */
	public static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

	/** SLUG와 같지만 빈 문자열도 허용한다. 수정 요청에서 빈 문자열은 값을 지운다는 뜻이다. */
	public static final String SLUG_OR_EMPTY = "^$|" + SLUG;

	/** 가격 상한(1조 원). 수량을 곱한 금액 계산이 long 범위를 넘어 음수가 되는 일을 막는다. */
	public static final long MAX_PRICE = 1_000_000_000_000L;

	private ValidationPatterns() {
	}
}
