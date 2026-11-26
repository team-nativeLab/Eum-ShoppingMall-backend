package com.example.shopping.domain.member.social;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * 카카오·구글 로그인 설정(#225). 키가 없는 제공자는 쓸 수 없다고 알리고(503), 나머지 기능은 그대로 동작한다.
 *
 * @param redirectUris 웹 로그인 요청의 redirectUri로 받을 주소. 프론트가 카카오·구글 콘솔에 등록한 콜백 주소다.
 */
@ConfigurationProperties("app.social")
public record SocialLoginProperties(@DefaultValue List<String> redirectUris, @DefaultValue Google google,
		@DefaultValue Kakao kakao) {

	public SocialLoginProperties {
		redirectUris = texts(redirectUris);
	}

	public boolean isAllowedRedirectUri(String redirectUri) {
		return redirectUri != null && redirectUris.contains(redirectUri);
	}

	/**
	 * @param clientId     웹 OAuth 클라이언트 ID
	 * @param clientSecret 웹 OAuth 클라이언트 비밀 값
	 * @param appClientIds 앱(Android·iOS) 클라이언트 ID. 앱이 보낸 ID 토큰의 대상(aud)으로 받는다.
	 */
	public record Google(String clientId, String clientSecret, @DefaultValue List<String> appClientIds) {

		public Google {
			appClientIds = texts(appClientIds);
		}

		public boolean webConfigured() {
			return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
		}

		/** 앱이 보낸 ID 토큰의 대상으로 받는 클라이언트 ID(웹 + 앱) */
		public List<String> idTokenAudiences() {
			List<String> audiences = new ArrayList<>(appClientIds);
			if (StringUtils.hasText(clientId)) {
				audiences.add(clientId);
			}
			return audiences;
		}
	}

	/**
	 * @param restApiKey   REST API 키(웹 로그인의 client_id)
	 * @param clientSecret 카카오 콘솔에서 Client Secret을 켰을 때만
	 * @param appId        앱 ID(숫자). 앱이 보낸 액세스 토큰이 이 앱에서 받은 것인지 확인한다.
	 * @param adminKey     Admin 키. 탈퇴·연결 해제 때 카카오 연결 끊기를 부른다. 없으면 부르지 않는다.
	 */
	public record Kakao(String restApiKey, String clientSecret, String appId, String adminKey) {

		public boolean webConfigured() {
			return StringUtils.hasText(restApiKey);
		}

		public boolean appConfigured() {
			return StringUtils.hasText(appId);
		}
	}

	private static List<String> texts(List<String> values) {
		return values == null ? List.of()
				: values.stream().filter(StringUtils::hasText).map(String::trim).toList();
	}
}
