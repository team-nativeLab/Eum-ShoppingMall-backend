package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.global.security.Totp;
import com.jayway.jsonpath.JsonPath;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;

/** 관리자 OTP 2단계 인증(#164). 테스트 설정은 OTP를 끄므로 여기서만 켠다. */
@SpringBootTest(properties = "security.admin.mfa-required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithAnonymousUser
class AdminMfaTests {

	private static final String PASSWORD = "test-pass-1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 처음_로그인하면_OTP를_등록하고_코드를_넣어야_토큰을_받는다() throws Exception {
		insertMember("first@example.com", "ADMIN");

		// 1단계: 비밀번호가 맞아도 토큰과 쿠키를 주지 않고 OTP 등록을 요구한다.
		MockHttpServletResponse login = adminLogin("first@example.com")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mfaRequired").value(true))
				.andExpect(jsonPath("$.data.mfaSetupRequired").value(true))
				.andExpect(jsonPath("$.data.mfaTokenExpiresIn").value(300))
				.andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andReturn().getResponse();
		String mfaToken = JsonPath.read(login.getContentAsString(), "$.data.mfaToken");

		verify(mfaToken, "123456")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_SETUP_REQUIRED"));

		String setup = postJson("/api/v1/admin/auth/mfa/setup", "{\"mfaToken\":\"" + mfaToken + "\"}")
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String secret = JsonPath.read(setup, "$.data.secret");
		assertThat(secret).matches("[A-Z2-7]{32}");
		assertThat((String) JsonPath.read(setup, "$.data.otpauthUri"))
				.startsWith("otpauth://totp/Eum%20Shopping:first%40example.com?secret=" + secret);
		// DB에는 비밀 키 원문이 없다.
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("select secret_ciphertext from admin_mfa", String.class))
				.startsWith("v1:").doesNotContain(secret);

		verify(mfaToken, wrongCode(secret))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_CODE_INVALID"));

		// 2단계: 맞는 코드를 넣으면 등록을 마치고 관리자 세션을 준다.
		MockHttpServletResponse verified = verify(mfaToken, currentCode(secret))
				.andExpect(status().isOk())
				.andReturn().getResponse();
		assertThat(verified.getHeader(HttpHeaders.SET_COOKIE)).contains("admin_refresh_token=", "Path=/api/v1/admin/auth");
		String accessToken = JsonPath.read(verified.getContentAsString(), "$.data.accessToken");
		mockMvc.perform(get("/api/v1/admin/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select count(*) from admin_mfa where enabled_at is not null",
				Long.class)).isOne();

		// 한 번 쓴 로그인 확인 토큰은 다시 쓸 수 없다.
		verify(mfaToken, currentCode(secret))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_TOKEN_INVALID"));

		assertThat(loginSummaries()).containsExactly(
				"관리자 비밀번호 확인(OTP 대기) 200",
				"관리자 OTP 확인 실패 400",
				"관리자 OTP 확인 실패 401",
				"관리자 OTP 등록·로그인 성공 200");
	}

	@Test
	void 등록한_뒤에는_로그인마다_새_코드를_요구하고_한_번_받은_코드는_다시_받지_않는다() throws Exception {
		insertMember("again@example.com", "ADMIN");
		String enrollToken = mfaToken("again@example.com");
		String secret = JsonPath.read(postJson("/api/v1/admin/auth/mfa/setup",
				"{\"mfaToken\":\"" + enrollToken + "\"}").andReturn().getResponse().getContentAsString(), "$.data.secret");
		String usedCode = currentCode(secret);
		verify(enrollToken, usedCode).andExpect(status().isOk());

		String mfaToken = JsonPath.read(adminLogin("again@example.com")
				.andExpect(jsonPath("$.data.mfaRequired").value(true))
				.andExpect(jsonPath("$.data.mfaSetupRequired").value(false))
				.andReturn().getResponse().getContentAsString(), "$.data.mfaToken");
		// 등록을 마치면 비밀 키를 다시 받을 수 없다. 기기를 바꾸려면 최고 관리자가 초기화한다.
		postJson("/api/v1/admin/auth/mfa/setup", "{\"mfaToken\":\"" + mfaToken + "\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_ALREADY_ENABLED"));

		verify(mfaToken, usedCode)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_CODE_INVALID"));
		verify(mfaToken, Totp.code(decode(secret), Totp.step(Instant.now()) + 1)).andExpect(status().isOk());
	}

	@Test
	void 로그인_확인_토큰_하나로는_5번까지만_틀리고_15분에_10번_틀리면_막고_알린다() throws Exception {
		insertMember("guess@example.com", "ADMIN");
		String secret = enroll("guess@example.com");
		double alertsBefore = lockAlerts();

		String first = mfaToken("guess@example.com");
		for (int i = 0; i < 5; i++) {
			verify(first, wrongCode(secret)).andExpect(status().isUnauthorized());
		}
		verify(first, currentCode(secret))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_TOKEN_INVALID"));

		// 비밀번호를 다시 넣어 새 토큰을 받아도 틀린 횟수는 이어서 센다.
		String second = mfaToken("guess@example.com");
		for (int i = 0; i < 5; i++) {
			verify(second, wrongCode(secret)).andExpect(status().isUnauthorized());
		}
		verify(mfaToken("guess@example.com"), currentCode(secret))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_LOCKED"));
		assertThat(lockAlerts()).isEqualTo(alertsBefore + 1);
	}

	@Test
	void 로그인_확인_토큰은_5분이_지나거나_비밀번호가_바뀌면_쓸_수_없다() throws Exception {
		long adminId = insertMember("stale@example.com", "ADMIN");
		String secret = enroll("stale@example.com");

		String expired = mfaToken("stale@example.com");
		jdbcTemplate.update("update admin_login_challenge set expires_at = ?", LocalDateTime.now().minusSeconds(1));
		entityManager.clear();
		verify(expired, currentCode(secret))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_TOKEN_INVALID"));

		String beforePasswordChange = mfaToken("stale@example.com");
		jdbcTemplate.update("update member set access_token_version = access_token_version + 1 where id = ?", adminId);
		entityManager.clear();
		verify(beforePasswordChange, currentCode(secret))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ADMIN_MFA_TOKEN_INVALID"));
	}

	@Test
	void 최고_관리자가_OTP를_초기화하면_그_관리자는_세션이_끊기고_다시_등록해야_한다() throws Exception {
		long adminId = insertMember("lost-phone@example.com", "ADMIN");
		long otherAdminId = insertMember("other-admin@example.com", "ADMIN");
		long superAdminId = insertMember("super@example.com", "SUPER_ADMIN");
		enroll("lost-phone@example.com");
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class,
				adminId)).isOne();

		mockMvc.perform(delete("/api/v1/admin/users/{userId}/mfa", adminId).with(admin(otherAdminId, "ROLE_ADMIN")))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/admin/users/{userId}/mfa", adminId).with(admin(superAdminId, "ROLE_SUPER_ADMIN")))
				.andExpect(status().isOk());
		entityManager.flush();

		assertThat(jdbcTemplate.queryForObject("select count(*) from admin_mfa where member_id = ?", Long.class,
				adminId)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class,
				adminId)).isZero();
		adminLogin("lost-phone@example.com").andExpect(jsonPath("$.data.mfaSetupRequired").value(true));
	}

	// ───────── 도우미 ─────────

	/** 로그인해 OTP를 등록하고 비밀 키(Base32)를 돌려준다. */
	private String enroll(String email) throws Exception {
		String mfaToken = mfaToken(email);
		String secret = JsonPath.read(postJson("/api/v1/admin/auth/mfa/setup", "{\"mfaToken\":\"" + mfaToken + "\"}")
				.andReturn().getResponse().getContentAsString(), "$.data.secret");
		verify(mfaToken, currentCode(secret)).andExpect(status().isOk());
		return secret;
	}

	private String mfaToken(String email) throws Exception {
		return JsonPath.read(adminLogin(email).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.mfaToken");
	}

	private ResultActions adminLogin(String email) throws Exception {
		return postJson("/api/v1/admin/auth/login", """
				{"email":"%s","password":"%s"}
				""".formatted(email, PASSWORD));
	}

	private ResultActions verify(String mfaToken, String code) throws Exception {
		return postJson("/api/v1/admin/auth/mfa/verify", """
				{"mfaToken":"%s","code":"%s"}
				""".formatted(mfaToken, code));
	}

	private ResultActions postJson(String url, String body) throws Exception {
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String currentCode(String secret) {
		return Totp.code(decode(secret), Totp.step(Instant.now()));
	}

	/** 앞뒤 칸까지 받으므로 그 세 칸의 코드와 모두 다른 코드 */
	private static String wrongCode(String secret) {
		long step = Totp.step(Instant.now());
		List<String> valid = List.of(Totp.code(decode(secret), step - 1), Totp.code(decode(secret), step),
				Totp.code(decode(secret), step + 1), Totp.code(decode(secret), step + 2));
		for (int candidate = 0; ; candidate++) {
			String code = "%06d".formatted(candidate);
			if (!valid.contains(code)) {
				return code;
			}
		}
	}

	/** RFC 4648 Base32(패딩 없음)를 바이트로 */
	private static byte[] decode(String base32) {
		String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (char c : base32.toCharArray()) {
			buffer = (buffer << 5) | alphabet.indexOf(c);
			bits += 5;
			if (bits >= 8) {
				out.write((buffer >>> (bits - 8)) & 0xff);
				bits -= 8;
			}
		}
		return out.toByteArray();
	}

	private List<String> loginSummaries() {
		entityManager.flush();
		return jdbcTemplate.queryForList("""
				select concat(summary, ' ', response_status) from admin_audit_log where action = 'LOGIN' order by id
				""", String.class);
	}

	private double lockAlerts() {
		Counter counter = meterRegistry.find("manual.check.required").tag("reason", "admin_mfa_locked").counter();
		return counter == null ? 0 : counter.count();
	}

	private RequestPostProcessor admin(long memberId, String authority) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority(authority), new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private long insertMember(String email, String role) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '관리자', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD), role);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}
}
