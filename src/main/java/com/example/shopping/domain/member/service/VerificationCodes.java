package com.example.shopping.domain.member.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 이메일 인증번호와 확인 토큰을 만들고 해시한다. DB에는 해시만 저장한다. */
final class VerificationCodes {

	/**
	 * 인증번호에 쓰는 문자. 영문 대소문자와 숫자에서 헷갈리기 쉬운 0·O·o, 1·I·l을 뺐다.
	 * 56자로 6자리를 만들어 경우의 수는 약 308억 개다(숫자 6자리는 100만 개).
	 */
	static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
	static final int CODE_LENGTH = 6;
	private static final String HMAC_ALGORITHM = "HmacSHA256";
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

	/**
	 * 같은 값이라도 이메일·용도가 다르면 해시가 달라지게 섞는다.
	 * 32바이트 난수인 확인 토큰과 Redis 키에만 쓴다. 경우의 수가 적은 인증번호는 keyedHash로 해시한다.
	 */
	static String hash(String... parts) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(String.join(":", parts).getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 서버 비밀 값에서 용도별 키를 뽑는다. 용도가 다르면 키도 달라 한 키를 여러 곳에 그대로 쓰지 않는다. */
	static byte[] deriveKey(String secret, String purpose) {
		return hmac(secret.getBytes(StandardCharsets.UTF_8), purpose);
	}

	/**
	 * 비밀 키를 섞은 해시(HMAC-SHA256). 키는 DB에 없으므로 DB 값만으로는 모든 인증번호를 넣어 보는 방식으로 풀 수 없다.
	 */
	static String keyedHash(byte[] key, String... parts) {
		return HexFormat.of().formatHex(hmac(key, String.join(":", parts)));
	}

	/** 비교 시간으로 맞은 글자 수를 알아내지 못하게 한다. */
	static boolean matches(String expectedHash, String actualHash) {
		return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
				actualHash.getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] hmac(byte[] key, String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
