package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.global.captcha.CaptchaVerifier;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 인증번호를 3번 틀리면 로봇 확인, 5번 틀리면 1시간 차단 (#130).
 * 테스트 설정은 이 제한을 끄므로 여기서만 켠다. 틀린 횟수는 테스트 사이에도 남으므로 테스트마다 다른 이메일을 쓴다.
 */
@SpringBootTest(properties = {
		"security.verification-lock.captcha-after-failures=3",
		"security.verification-lock.max-failures=5" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithAnonymousUser
@Import({ RecordingEmailSender.Config.class, VerificationLockTests.FakeCaptchaConfig.class })
class VerificationLockTests {

	private static final String PASSWORD = "test-pass-1234";
	private static final String CLIENT_IP = "203.0.113.30";
	/** 인증번호에는 0과 1을 쓰지 않으므로 언제나 틀린 번호다. */
	private static final String WRONG_CODE = "000000";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RecordingEmailSender mailbox;

	@Autowired
	private FakeCaptchaVerifier captcha;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void clear() {
		mailbox.clear();
		captcha.calls.clear();
	}

	@Test
	void 인증번호를_3번_틀리면_로봇_확인을_통과해야_다시_확인할_수_있다() throws Exception {
		String email = uniqueEmail();
		sendSignupCode(email).andExpect(status().isOk());
		String code = mailbox.lastTo(email).code();
		for (int i = 0; i < 3; i++) {
			confirm(email, WRONG_CODE, null).andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		}
		assertThat(captcha.calls).isEmpty();

		// 로봇 확인 없이는 인증번호를 비교하지 않으므로 틀린 횟수도 늘지 않는다.
		confirm(email, code, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
		confirm(email, code, FakeCaptchaVerifier.ROBOT)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
		confirm(email, code, FakeCaptchaVerifier.HUMAN)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verificationToken").exists());
		// 인증번호 확인 화면의 위젯(action=verify-code)에서 받은 토큰인지도 확인 서비스에 함께 묻는다.
		assertThat(captcha.calls).containsExactly("verify-code:" + FakeCaptchaVerifier.ROBOT + "@" + CLIENT_IP,
				"verify-code:" + FakeCaptchaVerifier.HUMAN + "@" + CLIENT_IP);
	}

	@Test
	void 인증번호를_5번_틀리면_1시간_동안_인증번호를_받거나_확인할_수_없다() throws Exception {
		String email = uniqueEmail();
		sendSignupCode(email).andExpect(status().isOk());
		String code = mailbox.lastTo(email).code();
		for (int i = 0; i < 3; i++) {
			confirm(email, WRONG_CODE, null).andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		}
		confirm(email, WRONG_CODE, FakeCaptchaVerifier.HUMAN)
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		confirm(email, WRONG_CODE, FakeCaptchaVerifier.HUMAN)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));

		// 맞는 번호도, 새 인증번호 요청도 받지 않는다.
		confirm(email, code, FakeCaptchaVerifier.HUMAN)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
		pretendResendIntervalPassed(email);
		sendSignupCode(email)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
		assertThat(mailbox.sentTo(email)).hasSize(1);
	}

	@Test
	void 로봇_확인_서비스가_응답하지_않으면_503으로_알리고_틀린_횟수는_늘리지_않는다() throws Exception {
		String email = uniqueEmail();
		sendSignupCode(email).andExpect(status().isOk());
		String code = mailbox.lastTo(email).code();
		for (int i = 0; i < 3; i++) {
			confirm(email, WRONG_CODE, null).andExpect(status().isBadRequest());
		}

		confirm(email, code, FakeCaptchaVerifier.UNAVAILABLE)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CAPTCHA_UNAVAILABLE"));
		confirm(email, code, FakeCaptchaVerifier.HUMAN).andExpect(status().isOk());
	}

	@Test
	void 비밀번호_재설정도_같은_제한을_받고_막힌_동안에는_메일을_보내지_않는다() throws Exception {
		String email = uniqueEmail();
		insertMember(email);
		requestReset(email).andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(5)).until(() -> mailbox.sentTo(email).size() == 1);
		String code = mailbox.lastTo(email).code();

		for (int i = 0; i < 3; i++) {
			resetPassword(email, WRONG_CODE, null).andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		}
		resetPassword(email, code, null).andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
		resetPassword(email, WRONG_CODE, FakeCaptchaVerifier.HUMAN)
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		resetPassword(email, WRONG_CODE, FakeCaptchaVerifier.HUMAN)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
		resetPassword(email, code, FakeCaptchaVerifier.HUMAN)
				.andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));

		// 재설정 요청 응답은 그대로 성공이지만 막힌 동안에는 인증번호를 만들지도 보내지도 않는다.
		pretendResendIntervalPassed(email);
		requestReset(email).andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select count(*) from email_verification where email = ?",
				Integer.class, email)).isEqualTo(1);
		assertThat(mailbox.sentTo(email)).hasSize(1);
		String passwordHash = jdbcTemplate.queryForObject("select password_hash from member where email = ?",
				String.class, email);
		assertThat(passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();
	}

	@Test
	void 가입되지_않은_이메일도_똑같이_세어_재설정_인증에서_가입_여부가_드러나지_않는다() throws Exception {
		String email = uniqueEmail();
		for (int i = 0; i < 3; i++) {
			resetPassword(email, WRONG_CODE, null).andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		}
		resetPassword(email, WRONG_CODE, null).andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));
		resetPassword(email, WRONG_CODE, FakeCaptchaVerifier.HUMAN)
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		resetPassword(email, WRONG_CODE, FakeCaptchaVerifier.HUMAN)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"));
	}

	@Test
	void 가입_인증과_비밀번호_재설정은_따로_센다() throws Exception {
		String email = uniqueEmail();
		for (int i = 0; i < 3; i++) {
			confirm(email, WRONG_CODE, null).andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		}
		resetPassword(email, WRONG_CODE, null).andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
	}

	private static String uniqueEmail() {
		return "lock-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

	private ResultActions sendSignupCode(String email) throws Exception {
		return postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + email + "\"}");
	}

	private ResultActions requestReset(String email) throws Exception {
		return postJson("/api/v1/auth/password-reset", "{\"email\":\"" + email + "\"}");
	}

	private ResultActions confirm(String email, String code, String captchaToken) throws Exception {
		return postJson("/api/v1/auth/email-verifications/confirm",
				"{\"email\":\"%s\",\"code\":\"%s\"%s}".formatted(email, code, captchaField(captchaToken)));
	}

	private ResultActions resetPassword(String email, String code, String captchaToken) throws Exception {
		return postJson("/api/v1/auth/password-reset/confirm",
				"{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"new-pass-5678\"%s}"
						.formatted(email, code, captchaField(captchaToken)));
	}

	private static String captchaField(String captchaToken) {
		return captchaToken == null ? "" : ",\"captchaToken\":\"" + captchaToken + "\"";
	}

	private ResultActions postJson(String url, String body) throws Exception {
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)
				.with(request -> {
					request.setRemoteAddr(CLIENT_IP);
					return request;
				}));
	}

	private void insertMember(String email) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '홍길동', 'USER', 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD));
	}

	/** 1분이 지나 다시 받을 수 있는 것처럼 이 이메일의 발송 시각을 2분 전으로 옮긴다. */
	private void pretendResendIntervalPassed(String email) {
		entityManager.flush();
		jdbcTemplate.update("update email_verification set created_at = ? where email = ?",
				LocalDateTime.now().minusMinutes(2), email);
		entityManager.clear();
	}

	/** 토큰 값으로 결과를 정하는 가짜 로봇 확인. 받은 토큰과 IP를 남긴다. */
	static class FakeCaptchaVerifier implements CaptchaVerifier {

		static final String HUMAN = "human-token";
		static final String ROBOT = "robot-token";
		static final String UNAVAILABLE = "unavailable-token";

		final List<String> calls = new CopyOnWriteArrayList<>();

		@Override
		public boolean isEnabled() {
			return true;
		}

		@Override
		public boolean verify(String token, String action, String remoteIp) {
			if (UNAVAILABLE.equals(token)) {
				throw new BusinessException(ErrorCode.CAPTCHA_UNAVAILABLE);
			}
			calls.add(action + ":" + token + "@" + remoteIp);
			return HUMAN.equals(token);
		}
	}

	@TestConfiguration
	static class FakeCaptchaConfig {

		@Bean
		@Primary
		FakeCaptchaVerifier fakeCaptchaVerifier() {
			return new FakeCaptchaVerifier();
		}
	}
}
