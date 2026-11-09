package com.example.shopping.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/** 다시 꺼내 써야 하는 비밀 값(관리자 OTP 비밀 키)의 암호화(#164) */
class SecretCipherTests {

	private static final String SECRET = "server-secret-0123456789abcdef0123";
	private static final byte[] VALUE = "otp-secret-20-bytes!".getBytes(StandardCharsets.US_ASCII);

	private final SecretCipher cipher = new SecretCipher(
			KeyedHash.deriveKey(SECRET, SecretHashes.ADMIN_MFA_SECRET_ENCRYPTION));

	@Test
	void 암호화한_값을_같은_키와_행으로_풀고_암호문에는_원래_값이_보이지_않는다() {
		String stored = cipher.encrypt(VALUE, "admin-mfa:1");

		assertThat(stored).startsWith("v1:").doesNotContain("otp-secret");
		assertThat(cipher.decrypt(stored, "admin-mfa:1")).isEqualTo(VALUE);
		// 같은 값이어도 매번 다른 암호문이 나온다(IV).
		assertThat(cipher.encrypt(VALUE, "admin-mfa:1")).isNotEqualTo(stored);
	}

	@Test
	void 다른_행의_값으로_바꿔치기하거나_키가_바뀌거나_암호문을_고치면_풀지_않는다() {
		String stored = cipher.encrypt(VALUE, "admin-mfa:1");

		assertThatIllegalStateException().isThrownBy(() -> cipher.decrypt(stored, "admin-mfa:2"));
		assertThatIllegalStateException().isThrownBy(() -> new SecretCipher(
				KeyedHash.deriveKey("other-secret-0123456789abcdef01234", SecretHashes.ADMIN_MFA_SECRET_ENCRYPTION))
				.decrypt(stored, "admin-mfa:1"));
		assertThatIllegalStateException().isThrownBy(() -> new SecretCipher(
				KeyedHash.deriveKey(SECRET, SecretHashes.REFRESH_TOKEN)).decrypt(stored, "admin-mfa:1"));

		byte[] packed = Base64.getDecoder().decode(stored.substring(3));
		packed[packed.length - 1] ^= 1;
		String tampered = "v1:" + Base64.getEncoder().encodeToString(packed);
		assertThatIllegalStateException().isThrownBy(() -> cipher.decrypt(tampered, "admin-mfa:1"));
		assertThatIllegalStateException().isThrownBy(() -> cipher.decrypt("plain-value", "admin-mfa:1"));
	}
}
