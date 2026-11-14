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
}
