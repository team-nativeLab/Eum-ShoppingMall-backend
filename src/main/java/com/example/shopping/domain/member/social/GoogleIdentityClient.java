package com.example.shopping.domain.member.social;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
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
 * 구글 로그인(#225). 웹은 인가 코드를 토큰으로 바꿔 받은 ID 토큰을, 앱은 Google Sign-In이 준 ID 토큰을 확인한다.
 * ID 토큰은 구글 공개 키(JWKS)로 서명과 만료를 확인하고, 발급자(iss)와 대상(aud, 우리 클라이언트 ID)을 확인한다.
 */
public class GoogleIdentityClient implements SocialIdentityClient {

	static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
	static final String JWK_SET_URL = "https://www.googleapis.com/oauth2/v3/certs";
	private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

	private final RestClient restClient;
	private final SocialLoginProperties.Google properties;
	private final JwtDecoder idTokenDecoder;

	/** @param idTokenDecoder 구글 공개 키로 서명·만료를 확인하는 디코더. 발급자·대상은 여기서 따로 확인한다. */
	public GoogleIdentityClient(RestClient restClient, SocialLoginProperties.Google properties,
			JwtDecoder idTokenDecoder) {
		this.restClient = restClient;
		this.properties = properties;
		this.idTokenDecoder = idTokenDecoder;
	}

	@Override
	public SocialProfile verifyAuthorizationCode(String code, String redirectUri, String codeVerifier) {
		if (!properties.webConfigured()) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", code);
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("redirect_uri", redirectUri);
		if (StringUtils.hasText(codeVerifier)) {
			form.add("code_verifier", codeVerifier);
		}
		TokenResponse response;
		try {
			response = restClient.post().uri(TOKEN_URL)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(TokenResponse.class);
		} catch (HttpClientErrorException e) {
			// 코드가 틀렸거나 이미 썼거나, redirectUri·codeVerifier가 인가 요청과 다르다.
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
		}
		if (response == null || !StringUtils.hasText(response.idToken())) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		}
		return profile(response.idToken(), List.of(properties.clientId()));
	}

	@Override
	public SocialProfile verifyAppToken(String idToken) {
		List<String> audiences = properties.idTokenAudiences();
		if (audiences.isEmpty()) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
		}
		return profile(idToken, audiences);
	}

	private SocialProfile profile(String idToken, Collection<String> audiences) {
		Jwt jwt;
		try {
			jwt = idTokenDecoder.decode(idToken);
		} catch (JwtException e) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		}
		List<String> tokenAudiences = jwt.getAudience();
		if (!ISSUERS.contains(jwt.getClaimAsString("iss")) || tokenAudiences == null
				|| tokenAudiences.stream().noneMatch(audiences::contains) || !StringUtils.hasText(jwt.getSubject())) {
			throw new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED);
		}
		Object verified = jwt.getClaims().get("email_verified");
		boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
		return new SocialProfile(SocialProvider.GOOGLE, jwt.getSubject(), jwt.getClaimAsString("email"), emailVerified,
				jwt.getClaimAsString("name"));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("id_token") String idToken) {
	}
}
