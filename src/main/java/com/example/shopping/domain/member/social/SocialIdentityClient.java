package com.example.shopping.domain.member.social;

/**
 * 카카오·구글에 사용자를 확인한다(#225). 사용자가 보낸 값을 믿지 않고 제공자 서버에 직접 묻는다.
 * 확인하지 못하면 401(SOCIAL_LOGIN_FAILED), 제공자에 연결하지 못하면 503(SOCIAL_PROVIDER_UNAVAILABLE),
 * 키가 없으면 503(SOCIAL_LOGIN_NOT_CONFIGURED)이다.
 */
public interface SocialIdentityClient {

	/** 웹: 프론트가 받은 인가 코드를 교환한다. redirectUri는 인가를 요청할 때와 같은 값이어야 한다. */
	SocialProfile verifyAuthorizationCode(String code, String redirectUri, String codeVerifier);

	/** 앱: 앱 SDK가 받은 토큰(구글 ID 토큰, 카카오 액세스 토큰)을 확인한다. 다른 앱용으로 발급된 토큰은 받지 않는다. */
	SocialProfile verifyAppToken(String token);

	/** 제공자 쪽 연결도 끊는다(탈퇴·연결 해제). 실패해도 예외를 던지지 않는다. */
	default void unlink(String providerUserId) {
	}
}
