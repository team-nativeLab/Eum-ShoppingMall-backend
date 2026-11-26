package com.example.shopping.domain.member.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 로그인(#225). 웹은 인가 코드를 액세스 토큰으로 바꾸고, 앱은 카카오 SDK가 준 액세스 토큰을 받는다.
 * 앱 토큰은 우리 앱(app_id)에서 받은 것인지 먼저 확인한다. 다른 앱의 토큰으로 남의 계정에 로그인하는 것을 막는다.
 * 사용자 정보는 카카오 서버(/v2/user/me)에서 읽는다.
 */
public class KakaoIdentityClient implements SocialIdentityClient {

	static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
	static final String TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";
	static final String USER_URL = "https://kapi.kakao.com/v2/user/me";
	static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

	private static final Logger log = LoggerFactory.getLogger(KakaoIdentityClient.class);

	private final RestClient restClient;
	private final SocialLoginProperties.Kakao properties;

	public KakaoIdentityClient(RestClient restClient, SocialLoginProperties.Kakao properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public SocialProfile verifyAuthorizationCode(String code, String redirectUri, String codeVerifier) {
		if (!properties.webConfigured()) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", properties.restApiKey());
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		if (StringUtils.hasText(properties.clientSecret())) {
			form.add("client_secret", properties.clientSecret());
		}
		if (StringUtils.hasText(codeVerifier)) {
			form.add("code_verifier", codeVerifier);
		}
		TokenResponse response = call(() -> restClient.post().uri(TOKEN_URL)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenResponse.class));
		if (response == null || !StringUtils.hasText(response.accessToken())) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		}
		return profile(response.accessToken());
	}

	@Override
	public SocialProfile verifyAppToken(String accessToken) {
		if (!properties.appConfigured()) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
		}
		TokenInfo info = call(() -> restClient.get().uri(TOKEN_INFO_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(TokenInfo.class));
		if (info == null || info.appId() == null || !properties.appId().trim().equals(String.valueOf(info.appId()))) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		}
		return profile(accessToken);
	}

	/** Admin 키가 없으면 부르지 않는다. 실패는 기록만 하고 넘어간다(카카오 콘솔에서 직접 끊을 수 있다). */
	@Override
	public void unlink(String providerUserId) {
		if (!StringUtils.hasText(properties.adminKey())) {
			return;
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("target_id_type", "user_id");
		form.add("target_id", providerUserId);
		try {
			restClient.post().uri(UNLINK_URL)
					.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			log.warn("카카오 연결 끊기 실패: user={}", providerUserId, e);
		}
	}

	private SocialProfile profile(String accessToken) {
		KakaoUser user = call(() -> restClient.get().uri(USER_URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(KakaoUser.class));
		if (user == null || user.id() == null) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		}
		KakaoUser.Account account = user.account();
		String email = account == null ? null : account.email();
		// 유효하고(is_email_valid) 인증된(is_email_verified) 이메일만 인증된 이메일로 본다.
		boolean emailVerified = account != null && Boolean.TRUE.equals(account.emailValid())
				&& Boolean.TRUE.equals(account.emailVerified());
		String name = account == null ? null
				: StringUtils.hasText(account.name()) ? account.name()
						: account.profile() == null ? null : account.profile().nickname();
		return new SocialProfile(SocialProvider.KAKAO, String.valueOf(user.id()), email, emailVerified, name);
	}

	/** 4xx는 토큰·코드가 틀린 것(401), 그 밖의 실패는 카카오에 연결하지 못한 것(503)으로 본다. */
	private static <T> T call(java.util.function.Supplier<T> request) {
		try {
			return request.get();
		} catch (HttpClientErrorException e) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenInfo(Long id, @JsonProperty("app_id") Long appId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KakaoUser(Long id, @JsonProperty("kakao_account") Account account) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Account(String email, @JsonProperty("is_email_valid") Boolean emailValid,
				@JsonProperty("is_email_verified") Boolean emailVerified, String name, Profile profile) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Profile(String nickname) {
		}
	}
}
