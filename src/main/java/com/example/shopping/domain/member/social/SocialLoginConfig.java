package com.example.shopping.domain.member.social;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

/**
 * 카카오·구글 연결(#225). 응답이 늦어도 로그인 요청이 오래 붙잡히지 않게 연결·응답 대기 시간을 제한한다.
 * 구글 ID 토큰 디코더는 빈으로 두지 않는다. 두면 Access 토큰 검증(SecurityConfig.jwtDecoder)과 섞인다.
 */
@Configuration
@EnableConfigurationProperties(SocialLoginProperties.class)
public class SocialLoginConfig {

	@Bean
	GoogleIdentityClient googleIdentityClient(SocialLoginProperties properties) {
		// 공개 키는 처음 확인할 때 받아 와 캐시한다(서버가 뜰 때 구글에 연결하지 않는다).
		NimbusJwtDecoder idTokenDecoder = NimbusJwtDecoder.withJwkSetUri(GoogleIdentityClient.JWK_SET_URL)
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.build();
		return new GoogleIdentityClient(restClient(), properties.google(), idTokenDecoder);
	}

	@Bean
	KakaoIdentityClient kakaoIdentityClient(SocialLoginProperties properties) {
		return new KakaoIdentityClient(restClient(), properties.kakao());
	}

	private static RestClient restClient() {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		return RestClient.builder().requestFactory(requestFactory).build();
	}
}
