package com.example.shopping.global.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 로봇 확인 토큰 확인 (#130). Cloudflare Turnstile의 siteverify 형식 */
class SiteverifyCaptchaVerifierTests {

	private static final String VERIFY_URL = CaptchaProperties.TURNSTILE_VERIFY_URL;

	private MockRestServiceServer server;
	private SiteverifyCaptchaVerifier verifier;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		verifier = new SiteverifyCaptchaVerifier(builder.build(),
				new CaptchaProperties(true, "captcha-secret", VERIFY_URL, Duration.ofSeconds(3)));
	}

	@Test
	void 비밀_키와_토큰과_클라이언트_IP를_폼으로_보내고_success를_읽는다() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("secret", "captcha-secret");
		form.add("response", "widget-token");
		form.add("remoteip", "203.0.113.30");
		server.expect(requestTo(VERIFY_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().formData(form))
				.andRespond(withSuccess("""
						{"success":true,"error-codes":[],"hostname":"shop.example.com"}
						""", MediaType.APPLICATION_JSON));

		assertThat(verifier.verify("widget-token", "203.0.113.30")).isTrue();
		server.verify();
	}

	@Test
	void 확인에_실패한_토큰은_false() {
		server.expect(requestTo(VERIFY_URL))
				.andRespond(withSuccess("""
						{"success":false,"error-codes":["invalid-input-response"]}
						""", MediaType.APPLICATION_JSON));

		assertThat(verifier.verify("forged-token", null)).isFalse();
	}

	@Test
	void 확인_서비스_오류나_해석할_수_없는_응답은_503으로_알린다() {
		server.expect(requestTo(VERIFY_URL)).andRespond(withServerError());
		assertUnavailable(() -> verifier.verify("widget-token", null));

		server.reset();
		server.expect(requestTo(VERIFY_URL)).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));
		assertUnavailable(() -> verifier.verify("widget-token", null));
	}

	@Test
	void 켰는데_비밀_키가_없으면_서버가_뜨지_않는다() {
		assertThatThrownBy(() -> new CaptchaProperties(true, " ", VERIFY_URL, Duration.ofSeconds(3)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CaptchaProperties(true, "secret", "http://captcha.example.com", Duration.ofSeconds(3)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new CaptchaProperties(false, null, VERIFY_URL, Duration.ofSeconds(3)).enabled()).isFalse();
	}

	private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_UNAVAILABLE));
	}
}
