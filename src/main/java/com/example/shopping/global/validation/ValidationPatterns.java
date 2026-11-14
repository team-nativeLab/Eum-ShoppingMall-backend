package com.example.shopping.global.validation;

/** 요청 DTO의 @Pattern에서 함께 쓰는 정규식 */
public final class ValidationPatterns {

	/** 값이 있다면 공백만으로 이뤄져서는 안 된다. 수정(PATCH) 요청의 선택 입력 문자열에 쓴다. */
	public static final String HAS_TEXT = "(?s).*\\S.*";

	/** http(s) 주소 또는 /로 시작하는 서버 내 경로. javascript: 같은 스킴을 막는다. */
	public static final String URL_OR_PATH = "^(https?://|/)\\S+$";

	/** URL_OR_PATH와 같지만 빈 문자열도 허용한다. 수정 요청에서 빈 문자열은 값을 지운다는 뜻이다. */
	public static final String URL_OR_PATH_OR_EMPTY = "^$|" + URL_OR_PATH;

	/**
	 * 사이트 안 경로. /로 시작해야 하고, 다른 사이트로 가는 //·/\ 시작(프로토콜 상대 주소)과 공백·제어 문자·역슬래시를 막는다.
	 * 관리자가 정하는 이동 경로(홈 편성 등)에 쓴다. 인코딩하지 않은 한글 검색어(/search?keyword=안전모)는 받는다.
	 */
	public static final String INTERNAL_PATH = "^/(?![/\\\\])[^\\s\\\\\\p{Cntrl}]*$";

	/** 연락처. 숫자 사이 하이픈은 있어도 되고 없어도 된다 (예: 010-1234-5678, 0212345678). 저장할 때는 ContactFormats.phone으로 뺀다. */
	public static final String PHONE = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$";

	/**
	 * 로그인 이메일에 쓸 수 있는 글자. 공백 없는 영문·숫자·기호(ASCII)만 허용하고, 형식은 @Email로 따로 검사한다.
	 * MySQL은 악센트를 구분하지 않고 비교할 수 있어서 'ádmin@…'이 'admin@…' 계정으로 잡히는 것을 막는다(#137).
	 */
	public static final String ASCII_EMAIL = "^[\\x21-\\x7E]+$";

	/** PHONE과 같지만 빈 문자열도 허용한다. 수정 요청에서 빈 문자열은 값을 지운다는 뜻이다. */
	public static final String PHONE_OR_EMPTY = "^$|" + PHONE;

	/** 주소에 쓰는 영문 이름. 영문 소문자·숫자를 하이픈으로 이은 형태 (예: head-protection) */
	public static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

	/** SLUG와 같지만 빈 문자열도 허용한다. 수정 요청에서 빈 문자열은 값을 지운다는 뜻이다. */
	public static final String SLUG_OR_EMPTY = "^$|" + SLUG;

	/** 가격 상한(1조 원). 수량을 곱한 금액 계산이 long 범위를 넘어 음수가 되는 일을 막는다. */
	public static final long MAX_PRICE = 1_000_000_000_000L;

	private ValidationPatterns() {
	}
}
