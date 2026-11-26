package com.example.shopping.domain.member.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 카카오·구글로 처음 온 사람의 가입(#225). 이메일은 카카오·구글이 알려 준 값으로 가입하고, 비밀번호는 없다.
 * 이용약관·개인정보 수집·이용 동의는 필수, 마케팅 수신 동의는 선택이다(이메일 가입과 같다).
 *
 * @param signupToken 소셜 로그인 응답의 signupToken(10분 동안 한 번만 쓴다)
 * @param name        이름. 소셜 로그인 응답의 name을 기본값으로 보여 준다.
 * @param phone       연락처(선택)
 */
public record SocialSignupRequest(
		@NotBlank(message = "가입 토큰을 보내 주세요.")
		@Size(max = 100, message = "가입 토큰이 올바르지 않습니다.")
		String signupToken,

		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		String name,

		@Pattern(regexp = ValidationPatterns.PHONE, message = "연락처 형식이 올바르지 않습니다.")
		String phone,

		@NotNull(message = "이용약관에 동의해 주세요.")
		@AssertTrue(message = "이용약관에 동의해 주세요.")
		Boolean termsAgreed,

		@NotNull(message = "개인정보 수집·이용에 동의해 주세요.")
		@AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.")
		Boolean privacyAgreed,

		Boolean marketingAgreed) {

	/** 가입 토큰은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "SocialSignupRequest[signupToken=****, name=" + name + ", phone=" + phone + ", termsAgreed="
				+ termsAgreed + ", privacyAgreed=" + privacyAgreed + ", marketingAgreed=" + marketingAgreed + "]";
	}
}
