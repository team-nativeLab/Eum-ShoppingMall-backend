package com.example.shopping.domain.member.service;

import java.security.SecureRandom;
import java.util.Base64;

/** 이메일 인증번호와 확인 토큰을 만든다. DB에는 서버 키로 만든 해시만 저장한다(SecretHashes). */
final class VerificationCodes {

	/**
	 * 인증번호에 쓰는 문자. 영문 대소문자와 숫자에서 헷갈리기 쉬운 0·O·o, 1·I·l을 뺐다.
	 * 56자로 6자리를 만들어 경우의 수는 약 308억 개다(숫자 6자리는 100만 개).
	 */
	static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
	static final int CODE_LENGTH = 6;
	private static final SecureRandom RANDOM = new SecureRandom();

	private VerificationCodes() {
	}

	/** 영문 대소문자와 숫자 6자리. 대소문자를 구분한다. */
	static String newCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
		}
		return code.toString();
	}

	/** 추측할 수 없는 확인 토큰 (URL에 그대로 넣을 수 있는 문자) */
	static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
