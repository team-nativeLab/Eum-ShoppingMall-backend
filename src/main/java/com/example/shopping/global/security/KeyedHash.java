package com.example.shopping.global.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 서버 비밀 키를 섞은 해시(HMAC-SHA256). 키는 DB·Redis에 없으므로 DB나 Redis가 새도 원래 값을 넣어 보며 맞힐 수 없다.
 * 용도마다 키를 따로 뽑아(SecretHashes.forPurpose) 한 용도의 해시를 다른 용도에 그대로 쓸 수 없게 한다.
 */
public final class KeyedHash {

	private static final String ALGORITHM = "HmacSHA256";

	private final byte[] key;

	private KeyedHash(byte[] key) {
		this.key = key;
	}

	/** 서버 비밀 값에서 용도별 키를 뽑는다. 같은 비밀 값·용도면 언제나 같은 키가 나온다. */
	public static KeyedHash derive(String secret, String purpose) {
		return new KeyedHash(deriveKey(secret, purpose));
	}

	/** 용도별 키(32바이트). 암호화 키(SecretCipher)도 같은 방법으로 용도 이름만 달리 뽑는다. */
	static byte[] deriveKey(String secret, String purpose) {
		return hmac(secret.getBytes(StandardCharsets.UTF_8), purpose);
	}

	/** 값들을 ':'로 이어 해시한다. 결과는 64자 소문자 16진수다. 같은 값이면 언제나 같은 해시가 나와 다시 찾을 때 쓸 수 있다. */
	public String hash(String... parts) {
		return HexFormat.of().formatHex(hmac(key, String.join(":", parts)));
	}

	/** 저장된 해시와 같은 값인지. 비교 시간으로 맞은 글자 수를 알아내지 못하게 한다. */
	public boolean matches(String storedHash, String... parts) {
		return storedHash != null && MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.US_ASCII),
				hash(parts).getBytes(StandardCharsets.US_ASCII));
	}

	private static byte[] hmac(byte[] key, String value) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
