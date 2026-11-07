package com.example.shopping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** SIGNUP_EMAIL_VERIFICATION_REQUIRED=true일 때 */
@SpringBootTest(properties = "app.auth.signup-email-verification-required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithAnonymousUser
class EmailVerificationRequiredTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 이메일_인증_토큰_없이는_가입할_수_없다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"new-member@example.com","password":"test-pass-1234","name":"홍길동","termsAgreed":true,"privacyAgreed":true}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"new-member@example.com","password":"test-pass-1234","name":"홍길동",
				 "termsAgreed":true,"privacyAgreed":true,"emailVerificationToken":"made-up-token"}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
	}

	@Test
	void 인증을_마치지_않으면_이미_가입된_이메일인지_알려_주지_않는다() throws Exception {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values ('member@example.com', 'hash', '홍길동', 'USER', 'ACTIVE', now(), now())
				""");

		// 가입된 이메일이어도 409가 아니라, 가입되지 않은 이메일과 같은 400을 준다(#140).
		for (String token : new String[] { null, "made-up-token" }) {
			String tokenField = token == null ? "" : ",\"emailVerificationToken\":\"" + token + "\"";
			mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
					{"email":"member@example.com","password":"test-pass-1234","name":"홍길동",
					 "termsAgreed":true,"privacyAgreed":true%s}
					""".formatted(tokenField)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
		}
	}
}
