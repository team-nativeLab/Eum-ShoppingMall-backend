package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.VerificationLockTests.FakeCaptchaVerifier;

/**
 * 로봇 확인을 켜면 로그인 실패로 막지 않고 로봇 확인을 요구한다 (#141).
 * 남이 일부러 틀려도 주인은 로봇 확인을 하고 로그인할 수 있다. 막는 동작(로봇 확인을 껐을 때)은 LoginLockTests에서 확인한다.
 */
@SpringBootTest(properties = {
		"security.login-lock.captcha-after-failures=3",
		"security.login-lock.max-failures=5" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithAnonymousUser
@Import(VerificationLockTests.FakeCaptchaConfig.class)
class LoginCaptchaTests {

	private static final String PASSWORD = "test-pass-1234";
	private static final String WRONG = "wrong-pass-1234";
	private static final String CLIENT_IP = "198.51.100.7";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private FakeCaptchaVerifier captcha;

	@BeforeEach
	void clear() {
		captcha.calls.clear();
		captcha.enabled = true;
	}

	@Test
	void 남이_일부러_틀려도_막지_않고_로봇_확인을_통과하면_로그인된다() throws Exception {
		String email = insertMember("USER");
		for (int i = 0; i < 3; i++) {
			login("/api/v1/auth/login", email, WRONG, null)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		}

		// 로봇 확인 없이는 비밀번호를 비교하지 않으므로 맞는 비밀번호로도 로그인되지 않는다.
		login("/api/v1/auth/login", email, PASSWORD, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
		login("/api/v1/auth/login", email, PASSWORD, FakeCaptchaVerifier.ROBOT)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
		// 막는 횟수(5번)를 넘겨 틀려도 429가 아니라 로봇 확인만 요구한다.
		for (int i = 0; i < 5; i++) {
			login("/api/v1/auth/login", email, WRONG, FakeCaptchaVerifier.HUMAN)
					.andExpect(status().isUnauthorized());
		}

		login("/api/v1/auth/login", email, PASSWORD, FakeCaptchaVerifier.HUMAN).andExpect(status().isOk());
		// 로그인에 성공하면 센 횟수를 지워 다음에는 로봇 확인 없이 로그인한다.
		login("/api/v1/auth/login", email, PASSWORD, null).andExpect(status().isOk());
		assertThat(captcha.calls).contains("login:" + FakeCaptchaVerifier.HUMAN + "@" + CLIENT_IP);
	}

	@Test
	void 관리자_로그인도_같은_규칙을_따른다() throws Exception {
		String email = insertMember("ADMIN");
		for (int i = 0; i < 3; i++) {
			login("/api/v1/admin/auth/login", email, WRONG, null).andExpect(status().isUnauthorized());
		}

		login("/api/v1/admin/auth/login", email, PASSWORD, null)
				.andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
		login("/api/v1/admin/auth/login", email, PASSWORD, FakeCaptchaVerifier.HUMAN).andExpect(status().isOk());
	}

	@Test
	void 로봇_확인을_끄면_지금처럼_막는다() throws Exception {
		captcha.enabled = false;
		String email = insertMember("USER");
		for (int i = 0; i < 5; i++) {
			login("/api/v1/auth/login", email, WRONG, null).andExpect(status().isUnauthorized());
		}

		login("/api/v1/auth/login", email, PASSWORD, FakeCaptchaVerifier.HUMAN)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
		assertThat(captcha.calls).isEmpty();
	}

	private String insertMember(String role) {
		String email = "captcha-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '홍길동', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD), role);
		return email;
	}

	private ResultActions login(String url, String email, String password, String captchaToken) throws Exception {
		String captchaField = captchaToken == null ? "" : ",\"captchaToken\":\"" + captchaToken + "\"";
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"%s}".formatted(email, password, captchaField))
				.with(request -> {
					request.setRemoteAddr(CLIENT_IP);
					return request;
				}));
	}
}
