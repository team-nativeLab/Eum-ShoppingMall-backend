package com.example.shopping.global.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param secret              HS256 서명 키
 * @param accessTokenExpiry   Access 토큰 수명(초)
 * @param refreshTokenExpiry  Refresh 토큰 수명(초)
 * @param refreshCookieSecure Refresh 쿠키를 HTTPS에서만 보낼지 여부. 로컬 HTTP 개발 환경에서만 끈다.
 * @param refreshCookieSameSite SameSite 정책. 서로 다른 사이트의 프론트엔드에서 쓸 때는 HTTPS와 함께 None으로 설정한다.
 */
@Validated
@ConfigurationProperties("security.jwt")
public record JwtProperties(
		@NotBlank @Size(min = 32, message = "JWT 서명 키는 32자 이상이어야 합니다.") String secret,
		@Positive long accessTokenExpiry,
		@Positive long refreshTokenExpiry,
		@DefaultValue("true") boolean refreshCookieSecure,
		@DefaultValue("Strict") String refreshCookieSameSite) {

	public JwtProperties {
		if (!"Strict".equals(refreshCookieSameSite) && !"Lax".equals(refreshCookieSameSite)
				&& !"None".equals(refreshCookieSameSite)) {
			throw new IllegalArgumentException("SameSite must be Strict, Lax, or None");
		}
		if ("None".equals(refreshCookieSameSite) && !refreshCookieSecure) {
			throw new IllegalArgumentException("SameSite=None requires Secure cookies");
		}
	}

	public SecretKey secretKey() {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
