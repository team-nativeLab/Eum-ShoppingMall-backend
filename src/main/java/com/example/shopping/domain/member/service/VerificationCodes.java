package com.example.shopping.domain.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 이메일 인증번호와 확인 토큰을 만들고 해시한다. DB에는 해시만 저장한다. */
final class VerificationCodes {

	private static final SecureRandom RANDOM = new SecureRandom();

	private VerificationCodes() {
	}

	/** 6자리 숫자 */
	static String newCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	/** 추측할 수 없는 확인 토큰 (URL에 그대로 넣을 수 있는 문자) */
	static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** 같은 인증번호라도 이메일·용도가 다르면 해시가 달라지게 섞는다. */
	static String hash(String... parts) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(String.join(":", parts).getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 비교 시간으로 맞은 글자 수를 알아내지 못하게 한다. */
	static boolean matches(String expectedHash, String actualHash) {
		return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
				actualHash.getBytes(StandardCharsets.UTF_8));
	}
}
