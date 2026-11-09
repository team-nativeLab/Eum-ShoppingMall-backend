package com.example.shopping.global.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 서버가 원래 값을 다시 꺼내 써야 하는 비밀 값(관리자 OTP 비밀 키, #164)을 AES-256-GCM으로 암호화한다.
 * 해시로는 원래 값을 되돌릴 수 없어 OTP 코드를 계산할 수 없기 때문이다. 키는 DB에 없으므로 DB가 새도 값을 풀 수 없다.
 * 저장 형식은 "v1:" + Base64(IV 12바이트 + 암호문 + 인증 태그)다. 다른 행의 값으로 바꿔치기하지 못하게 행 식별자를 함께 묶는다.
 */
public final class SecretCipher {

	private static final String PREFIX = "v1:";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	SecretCipher(byte[] key) {
		this.key = new SecretKeySpec(key, "AES");
	}

	/** @param boundTo 이 값이 속한 행의 식별자(예: 회원 ID). 풀 때도 같은 값을 줘야 한다. */
	public String encrypt(byte[] plaintext, String boundTo) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(boundTo.getBytes(StandardCharsets.UTF_8));
			byte[] encrypted = cipher.doFinal(plaintext);
			return PREFIX + Base64.getEncoder().encodeToString(
					ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 키가 다르거나(JWT_SECRET을 바꾼 경우) 값이 바뀌었으면 IllegalStateException */
	public byte[] decrypt(String stored, String boundTo) {
		if (stored == null || !stored.startsWith(PREFIX)) {
			throw new IllegalStateException("알 수 없는 암호문 형식입니다.");
		}
		try {
			byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES));
			cipher.updateAAD(boundTo.getBytes(StandardCharsets.UTF_8));
			return cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("암호문을 풀 수 없습니다.", e);
		}
	}
}
