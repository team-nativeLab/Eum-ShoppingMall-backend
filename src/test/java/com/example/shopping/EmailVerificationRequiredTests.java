package com.example.shopping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

	@Test
	void 이메일_인증_토큰_없이는_가입할_수_없다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"new-member@example.com","password":"test-pass-1234","name":"홍길동"}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"new-member@example.com","password":"test-pass-1234","name":"홍길동",
				 "emailVerificationToken":"made-up-token"}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
	}
}
