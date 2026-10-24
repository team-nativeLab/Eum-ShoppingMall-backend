package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.example.shopping.domain.member.controller.AuthTokenResponder;
import com.example.shopping.domain.member.dto.AuthTokens;
import com.example.shopping.global.security.JwtProperties;

class JwtPropertiesTests {

	@Test
	void 기본_Refresh_쿠키는_Strict다() {
		JwtProperties properties = new JwtProperties("x".repeat(32), 900, 1209600, true, "Strict");
		assertThat(properties.refreshCookieSameSite()).isEqualTo("Strict");
	}

	@Test
	void 교차_사이트_쿠키는_Secure가_필요하다() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new JwtProperties("x".repeat(32), 900, 1209600, false, "None"));
		assertThat(new JwtProperties("x".repeat(32), 900, 1209600, true, "None").refreshCookieSameSite())
				.isEqualTo("None");
	}

	@Test
	void 교차_사이트_Refresh_쿠키는_Secure와_SameSite_None을_설정한다() {
		JwtProperties properties = new JwtProperties("x".repeat(32), 900, 1209600, true, "None");
		String cookie = new AuthTokenResponder(properties)
				.withTokens(new AuthTokens("access", 900, "refresh"))
				.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
		assertThat(cookie).contains("SameSite=None", "Secure", "HttpOnly");
	}

	@Test
	void 지원하지_않는_SameSite_값을_거부한다() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new JwtProperties("x".repeat(32), 900, 1209600, true, "Invalid"));
	}
}
