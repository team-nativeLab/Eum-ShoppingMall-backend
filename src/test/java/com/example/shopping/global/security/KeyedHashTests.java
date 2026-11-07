package com.example.shopping.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/** 서버 키를 섞은 해시(#136) */
class KeyedHashTests {

	private static final String SECRET = "server-secret-0123456789abcdef0123";

	@Test
	void 같은_값이면_같은_해시이고_키_용도_값이_다르면_달라진다() {
		KeyedHash hashes = KeyedHash.derive(SECRET, SecretHashes.EMAIL_VERIFICATION_CODE);

		String hash = hashes.hash("SIGNUP", "member@example.com", "Ab3dE9");

		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}")
				.isEqualTo(hashes.hash("SIGNUP", "member@example.com", "Ab3dE9"))
				.isNotEqualTo(hashes.hash("SIGNUP", "member@example.com", "aB3De9"))
				.isNotEqualTo(KeyedHash.derive("other-secret-0123456789abcdef01234", SecretHashes.EMAIL_VERIFICATION_CODE)
						.hash("SIGNUP", "member@example.com", "Ab3dE9"))
				.isNotEqualTo(KeyedHash.derive(SECRET, SecretHashes.LOGIN_LOCK_KEY)
						.hash("SIGNUP", "member@example.com", "Ab3dE9"))
				// 키 없이 계산하는 SHA-256과 다르다. DB·Redis 값만으로는 원래 값을 넣어 보며 맞힐 수 없다.
				.isNotEqualTo(sha256Hex("SIGNUP:member@example.com:Ab3dE9"));
	}

	@Test
	void 이전에_보낸_인증번호도_맞도록_PR_135와_같은_방식으로_키를_뽑고_해시한다() throws Exception {
		// 키 = HMAC(비밀 값, 용도), 해시 = HMAC(키, "값:값:값")
		byte[] key = hmac(SECRET.getBytes(StandardCharsets.UTF_8), "email-verification-code");
		String expected = HexFormat.of().formatHex(hmac(key, "SIGNUP:member@example.com:Ab3dE9"));

		assertThat(KeyedHash.derive(SECRET, SecretHashes.EMAIL_VERIFICATION_CODE)
				.hash("SIGNUP", "member@example.com", "Ab3dE9")).isEqualTo(expected);
	}

	@Test
	void 저장된_해시와_비교한다() {
		KeyedHash hashes = KeyedHash.derive(SECRET, SecretHashes.REFRESH_TOKEN);
		String stored = hashes.hash("token");

		assertThat(hashes.matches(stored, "token")).isTrue();
		assertThat(hashes.matches(stored, "other-token")).isFalse();
		assertThat(hashes.matches(null, "token")).isFalse();
	}

	private static byte[] hmac(byte[] key, String value) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String sha256Hex(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
