package com.example.shopping.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 웹 카카오·구글 로그인(#225). 프론트가 카카오·구글 로그인 화면에서 돌아올 때 받은 값을 그대로 보낸다.
 *
 * @param code         돌아올 주소에 붙어 온 인가 코드(한 번만 쓸 수 있다)
 * @param redirectUri  인가를 요청할 때 쓴 돌아올 주소. 서버에 등록한 주소(SOCIAL_REDIRECT_URIS) 중 하나여야 한다.
 * @param codeVerifier PKCE를 썼다면 인가 요청의 code_challenge를 만든 원래 값
 */
public record SocialLoginRequest(
		@NotBlank(message = "인가 코드를 보내 주세요.")
		@Size(max = 2048, message = "인가 코드가 너무 깁니다.")
		String code,

		@NotBlank(message = "돌아올 주소를 보내 주세요.")
		@Size(max = 500, message = "돌아올 주소가 너무 깁니다.")
		String redirectUri,

		@Size(max = 200, message = "code_verifier가 너무 깁니다.")
		String codeVerifier) {

	/** 인가 코드와 code_verifier는 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "SocialLoginRequest[code=****, redirectUri=" + redirectUri + ", codeVerifier="
				+ (codeVerifier == null ? null : "****") + "]";
	}
}
