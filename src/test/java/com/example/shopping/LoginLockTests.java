package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

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

/** 이메일별 로그인 실패 제한 (#105). 테스트 설정은 이 제한을 끄므로 여기서만 3번으로 켠다. */
@SpringBootTest(properties = "security.login-lock.max-failures=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithAnonymousUser
@Import(RecordingEmailSender.Config.class)
class LoginLockTests {

	private static final String PASSWORD = "test-pass-1234";
	private static final String WRONG = "wrong-pass-1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RecordingEmailSender mailbox;

	@Test
	void 한_이메일로_3번_틀리면_IP가_달라도_맞는_비밀번호로_로그인할_수_없다() throws Exception {
		insertMember("locked@example.com", "USER");
		insertMember("other@example.com", "USER");
		for (int i = 0; i < 3; i++) {
			login("locked@example.com", WRONG, "203.0.113." + i).andExpect(status().isUnauthorized());
		}

		login("locked@example.com", PASSWORD, "198.51.100.1")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
		// 대소문자만 다른 이메일도 같은 계정으로 센다.
		login("LOCKED@Example.com", PASSWORD, "198.51.100.2").andExpect(status().isTooManyRequests());
		login("other@example.com", PASSWORD, "198.51.100.1").andExpect(status().isOk());
	}

	@Test
	void 없는_이메일도_똑같이_막아_가입_여부가_드러나지_않는다() throws Exception {
		for (int i = 0; i < 3; i++) {
			login("nobody@example.com", WRONG, "203.0.113.9").andExpect(status().isUnauthorized());
		}
		login("nobody@example.com", WRONG, "203.0.113.9")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
	}

	@Test
	void 로그인에_성공하면_센_횟수를_지운다() throws Exception {
		insertMember("success@example.com", "USER");
		for (int round = 0; round < 2; round++) {
			login("success@example.com", WRONG, "203.0.113.5").andExpect(status().isUnauthorized());
			login("success@example.com", WRONG, "203.0.113.5").andExpect(status().isUnauthorized());
			login("success@example.com", PASSWORD, "203.0.113.5").andExpect(status().isOk());
		}
	}

	@Test
	void 관리자_로그인도_막고_막힌_시도를_감사_로그에_남긴다() throws Exception {
		insertMember("lock-admin@example.com", "ADMIN");
		for (int i = 0; i < 3; i++) {
			postJson("/api/v1/admin/auth/login", credentials("lock-admin@example.com", WRONG))
					.andExpect(status().isUnauthorized());
		}
		postJson("/api/v1/admin/auth/login", credentials("lock-admin@example.com", PASSWORD))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));

		assertThat(jdbcTemplate.queryForList("""
				select response_status from admin_audit_log where action = 'LOGIN' and details like ? order by id
				""", Integer.class, "%lock-admin@example.com%")).containsExactly(401, 401, 401, 429);
	}

	@Test
	void 비밀번호를_재설정하면_막힌_로그인이_풀린다() throws Exception {
		insertMember("reset-lock@example.com", "USER");
		for (int i = 0; i < 3; i++) {
			login("reset-lock@example.com", WRONG, "203.0.113.7").andExpect(status().isUnauthorized());
		}
		login("reset-lock@example.com", PASSWORD, "203.0.113.7").andExpect(status().isTooManyRequests());

		postJson("/api/v1/auth/password-reset", "{\"email\":\"reset-lock@example.com\"}").andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(5)).until(() -> !mailbox.sentTo("reset-lock@example.com").isEmpty());
		postJson("/api/v1/auth/password-reset/confirm", """
				{"email":"reset-lock@example.com","code":"%s","newPassword":"new-pass-5678"}
				""".formatted(mailbox.lastTo("reset-lock@example.com").code()))
				.andExpect(status().isOk());

		login("reset-lock@example.com", "new-pass-5678", "203.0.113.7").andExpect(status().isOk());
	}

	private ResultActions login(String email, String password, String clientIp) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, password))
				.with(request -> {
					request.setRemoteAddr(clientIp);
					return request;
				}));
	}

	private ResultActions postJson(String url, String body) throws Exception {
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String credentials(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	private void insertMember(String email, String role) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '홍길동', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD), role);
	}
}
