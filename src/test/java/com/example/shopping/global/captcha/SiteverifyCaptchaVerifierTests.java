package com.example.shopping.global.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

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

/** 로봇 확인 토큰 확인 (#130, #146). Cloudflare Turnstile의 siteverify 형식 */
class SiteverifyCaptchaVerifierTests {

	private static final String VERIFY_URL = CaptchaProperties.TURNSTILE_VERIFY_URL;
	private static final String ACTION = "verify-code";

	private MockRestServiceServer server;
	private SiteverifyCaptchaVerifier verifier;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		verifier = new SiteverifyCaptchaVerifier(builder.build(), new CaptchaProperties(true, "captcha-secret",
				List.of("shop.example.com", " Admin.Example.com "), VERIFY_URL, Duration.ofSeconds(3)));
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
						{"success":true,"error-codes":[],"hostname":"shop.example.com","action":"verify-code"}
						""", MediaType.APPLICATION_JSON));

		assertThat(verifier.verify("widget-token", ACTION, "203.0.113.30")).isTrue();
		server.verify();
	}

	@Test
	void 확인에_실패한_토큰은_false() {
		respond("""
				{"success":false,"error-codes":["invalid-input-response"]}
				""");

		assertThat(verifier.verify("forged-token", ACTION, null)).isFalse();
	}

	@Test
	void 허용한_사이트에서_이_용도로_푼_토큰만_통과시킨다() {
		// 허용 목록은 앞뒤 공백과 대소문자를 가리지 않는다.
		respond("""
				{"success":true,"hostname":"ADMIN.example.com","action":"verify-code"}
				""");
		assertThat(verifier.verify("widget-token", ACTION, null)).isTrue();

		server.reset();
		respond("""
				{"success":true,"hostname":"evil.example.net","action":"verify-code"}
				""");
		assertThat(verifier.verify("widget-token", ACTION, null)).as("다른 사이트").isFalse();

		server.reset();
		respond("""
				{"success":true,"hostname":"shop.example.com","action":"newsletter"}
				""");
		assertThat(verifier.verify("widget-token", ACTION, null)).as("다른 용도의 위젯").isFalse();

		server.reset();
		respond("""
				{"success":true}
				""");
		assertThat(verifier.verify("widget-token", ACTION, null)).as("사이트·용도가 없는 응답").isFalse();
	}

	@Test
	void 확인_서비스_오류나_해석할_수_없는_응답은_503으로_알린다() {
		server.expect(requestTo(VERIFY_URL)).andRespond(withServerError());
		assertUnavailable(() -> verifier.verify("widget-token", ACTION, null));

		server.reset();
		server.expect(requestTo(VERIFY_URL)).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));
		assertUnavailable(() -> verifier.verify("widget-token", ACTION, null));
	}

	@Test
	void 켰는데_비밀_키나_허용_사이트가_없으면_서버가_뜨지_않는다() {
		List<String> hostnames = List.of("shop.example.com");
		assertThatThrownBy(() -> new CaptchaProperties(true, " ", hostnames, VERIFY_URL, Duration.ofSeconds(3)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CaptchaProperties(true, "secret", List.of(" "), VERIFY_URL, Duration.ofSeconds(3)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CAPTCHA_ALLOWED_HOSTNAMES");
		assertThatThrownBy(() -> new CaptchaProperties(true, "secret", hostnames, "http://captcha.example.com",
				Duration.ofSeconds(3)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new CaptchaProperties(false, null, null, VERIFY_URL, Duration.ofSeconds(3)).enabled()).isFalse();
	}

	private void respond(String body) {
		server.expect(requestTo(VERIFY_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_UNAVAILABLE));
	}
}
