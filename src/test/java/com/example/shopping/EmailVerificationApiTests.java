package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.service.EmailVerificationCleaner;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** 회원가입 이메일 인증과 비밀번호 재설정 (#19) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithAnonymousUser
@Import(RecordingEmailSender.Config.class)
class EmailVerificationApiTests {

	private static final String EMAIL = "new-member@example.com";
	private static final String PASSWORD = "test-pass-1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RecordingEmailSender mailbox;

	@Autowired
	private EmailVerificationCleaner cleaner;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void clearMailbox() {
		mailbox.clear();
	}

	// ───────── 회원가입 이메일 인증 ─────────

	@Test
	void 이메일로_받은_인증번호를_확인하고_받은_토큰으로_가입하면_인증된_계정이_된다() throws Exception {
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"New-Member@Example.com\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresAt").exists());
		RecordingEmailSender.SentMail mail = mailbox.lastTo(EMAIL);
		assertThat(mail.subject()).contains("이메일 인증번호");

		confirm(EMAIL, wrong(mail.code()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		String token = JsonPath.read(confirm(EMAIL, mail.code())
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.verificationToken");

		signup(EMAIL, token).andExpect(status().isCreated());
		assertThat(jdbcTemplate.queryForObject("select email_verified_at from member where email = ?",
				LocalDateTime.class, EMAIL)).isNotNull();

		// 토큰은 한 번만 쓸 수 있고, 다른 이메일로 가입할 때도 쓸 수 없다.
		signup("other@example.com", token)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
	}

	@Test
	void 다른_이메일로_받은_토큰으로는_가입할_수_없다() throws Exception {
		String token = verifiedToken("first@example.com");

		signup("second@example.com", token)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
		signup("first@example.com", token).andExpect(status().isCreated());
	}

	@Test
	void 이미_가입된_이메일은_인증번호를_보내지_않고_1분_안에는_다시_보낼_수_없다() throws Exception {
		insertMember("member@example.com");
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"member@example.com\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

		postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + EMAIL + "\"}").andExpect(status().isOk());
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + EMAIL + "\"}")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("VERIFICATION_RESEND_TOO_SOON"));
		assertThat(mailbox.sentTo(EMAIL)).hasSize(1);
		assertThat(mailbox.sentTo("member@example.com")).isEmpty();
	}

	@Test
	void 인증번호를_5번_틀리면_맞는_번호도_받지_않고_새로_받아야_한다() throws Exception {
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + EMAIL + "\"}").andExpect(status().isOk());
		String code = mailbox.lastTo(EMAIL).code();

		for (int i = 0; i < 4; i++) {
			confirm(EMAIL, wrong(code)).andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		}
		confirm(EMAIL, wrong(code)).andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));
		confirm(EMAIL, code)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_ATTEMPTS_EXCEEDED"));

		// 1분이 지나 새 인증번호를 받으면 다시 확인할 수 있다.
		jdbcTemplate.update("update email_verification set created_at = ? where email = ?",
				LocalDateTime.now().minusMinutes(2), EMAIL);
		entityManager.clear();
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + EMAIL + "\"}").andExpect(status().isOk());
		confirm(EMAIL, mailbox.lastTo(EMAIL).code()).andExpect(status().isOk());
	}

	@Test
	void 만료된_인증번호는_받지_않는다() throws Exception {
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + EMAIL + "\"}").andExpect(status().isOk());
		jdbcTemplate.update("update email_verification set expires_at = ? where email = ?",
				LocalDateTime.now().minusSeconds(1), EMAIL);
		entityManager.clear();

		confirm(EMAIL, mailbox.lastTo(EMAIL).code())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
	}

	@Test
	void 인증_필수_설정이_꺼져_있으면_토큰_없이도_가입할_수_있다() throws Exception {
		signup(EMAIL, null).andExpect(status().isCreated());
		assertThat(jdbcTemplate.queryForObject("select email_verified_at from member where email = ?",
				LocalDateTime.class, EMAIL)).isNull();
	}

	// ───────── 비밀번호 재설정 ─────────

	@Test
	void 인증번호로_비밀번호를_바꾸면_새_비밀번호로만_로그인되고_기존_로그인은_모두_끊긴다() throws Exception {
		insertMember("member@example.com");
		String oldAccessToken = JsonPath.read(login("member@example.com", PASSWORD)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.accessToken");

		postJson("/api/v1/auth/password-reset", "{\"email\":\"member@example.com\"}").andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(5)).until(() -> !mailbox.sentTo("member@example.com").isEmpty());
		RecordingEmailSender.SentMail mail = mailbox.lastTo("member@example.com");
		assertThat(mail.subject()).contains("비밀번호 재설정");

		resetPassword("member@example.com", wrong(mail.code()), "new-pass-5678")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
		resetPassword("member@example.com", mail.code(), "new-pass-5678").andExpect(status().isOk());

		login("member@example.com", PASSWORD).andExpect(status().isUnauthorized());
		login("member@example.com", "new-pass-5678").andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldAccessToken))
				.andExpect(status().isUnauthorized());
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from refresh_token t join member m on m.id = t.member_id
				where m.email = 'member@example.com' and t.used_at is null
				""", Integer.class)).isEqualTo(1);

		// 같은 인증번호로 다시 바꿀 수 없다.
		resetPassword("member@example.com", mail.code(), "other-pass-9999")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
	}

	@Test
	void 가입되지_않았거나_정지된_이메일도_같은_응답을_받고_메일은_가지_않는다() throws Exception {
		insertMember("suspended@example.com");
		jdbcTemplate.update("update member set status = 'SUSPENDED' where email = 'suspended@example.com'");

		postJson("/api/v1/auth/password-reset", "{\"email\":\"nobody@example.com\"}")
				.andExpect(status().isOk());
		postJson("/api/v1/auth/password-reset", "{\"email\":\"suspended@example.com\"}")
				.andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForObject("select count(*) from email_verification", Integer.class)).isZero();
		resetPassword("nobody@example.com", "123456", "new-pass-5678")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
	}

	@Test
	void 하루가_지난_인증번호_기록은_정리된다() throws Exception {
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + EMAIL + "\"}").andExpect(status().isOk());
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"fresh@example.com\"}").andExpect(status().isOk());
		jdbcTemplate.update("update email_verification set created_at = ? where email = ?",
				LocalDateTime.now().minusDays(2), EMAIL);

		assertThat(cleaner.purge(LocalDateTime.now().minusDays(1))).isEqualTo(1);
		assertThat(jdbcTemplate.queryForList("select email from email_verification", String.class))
				.containsExactly("fresh@example.com");
	}

	private String verifiedToken(String email) throws Exception {
		postJson("/api/v1/auth/email-verifications", "{\"email\":\"" + email + "\"}").andExpect(status().isOk());
		return JsonPath.read(confirm(email, mailbox.lastTo(email).code())
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.verificationToken");
	}

	private ResultActions confirm(String email, String code) throws Exception {
		return postJson("/api/v1/auth/email-verifications/confirm",
				"{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code));
	}

	private ResultActions signup(String email, String token) throws Exception {
		String tokenField = token == null ? "" : ",\"emailVerificationToken\":\"" + token + "\"";
		return postJson("/api/v1/auth/signup",
				"{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"홍길동\"%s}".formatted(email, PASSWORD, tokenField));
	}

	private ResultActions resetPassword(String email, String code, String newPassword) throws Exception {
		return postJson("/api/v1/auth/password-reset/confirm",
				"{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"%s\"}".formatted(email, code, newPassword));
	}

	private ResultActions login(String email, String password) throws Exception {
		return postJson("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
	}

	private ResultActions postJson(String url, String body) throws Exception {
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void insertMember(String email) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '홍길동', 'USER', 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD));
	}

	/** 맞는 인증번호와 다른 6자리 숫자 */
	private String wrong(String code) {
		return code.equals("000000") ? "111111" : "000000";
	}
}
