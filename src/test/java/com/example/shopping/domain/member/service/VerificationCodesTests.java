package com.example.shopping.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 인증번호 형식과 해시 (#130) */
class VerificationCodesTests {

	@Test
	void 인증번호는_헷갈리는_글자를_뺀_영문_대소문자와_숫자_6자리다() {
		Set<Character> used = new HashSet<>();
		for (int i = 0; i < 2_000; i++) {
			String code = VerificationCodes.newCode();
			assertThat(code).matches("^[A-HJ-NP-Za-km-z2-9]{6}$");
			code.chars().forEach(c -> used.add((char) c));
		}
		assertThat(used).anyMatch(Character::isUpperCase)
				.anyMatch(Character::isLowerCase)
				.anyMatch(Character::isDigit)
				.doesNotContain('0', '1', 'O', 'o', 'I', 'l');
		assertThat(VerificationCodes.CODE_ALPHABET).hasSize(56);
	}

	@Test
	void 인증번호_해시는_서버_비밀_키가_다르면_달라진다() {
		byte[] key = VerificationCodes.deriveKey("server-secret-0123456789abcdef0123", "email-verification-code");
		byte[] otherKey = VerificationCodes.deriveKey("other-secret-0123456789abcdef01234", "email-verification-code");

		String hash = VerificationCodes.keyedHash(key, "SIGNUP", "member@example.com", "Ab3dE9");
		assertThat(hash).hasSize(64)
				.isEqualTo(VerificationCodes.keyedHash(key, "SIGNUP", "member@example.com", "Ab3dE9"))
				.isNotEqualTo(VerificationCodes.keyedHash(otherKey, "SIGNUP", "member@example.com", "Ab3dE9"))
				.isNotEqualTo(VerificationCodes.keyedHash(key, "SIGNUP", "member@example.com", "aB3De9"))
				// 키 없이 계산하는 SHA-256과도 다르다.
				.isNotEqualTo(VerificationCodes.hash("SIGNUP", "member@example.com", "Ab3dE9"));
		assertThat(VerificationCodes.deriveKey("server-secret-0123456789abcdef0123", "other-purpose"))
				.isNotEqualTo(key);
	}
}
