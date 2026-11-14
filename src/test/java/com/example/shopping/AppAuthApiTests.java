package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** 앱 인증(#203): Refresh 토큰을 쿠키가 아닌 본문으로 주고받고, 브라우저 요청은 받지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppAuthApiTests {

	private static final String EMAIL = "app-member@example.com";
	private static final String PASSWORD = "test-pass-1234";
	private static final String LOGIN_BODY = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void 앱_로그인은_두_토큰을_본문으로_주고_쿠키를_쓰지_않는다() throws Exception {
		insertMember(EMAIL, "USER");

		String body = login(LOGIN_BODY)
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andExpect(jsonPath("$.data.expiresIn").value(900))
				.andExpect(jsonPath("$.data.refreshToken").isString())
				.andExpect(jsonPath("$.data.refreshTokenExpiresIn").isNumber())
				.andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.data.accessToken");

		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(EMAIL));
	}

	@Test
	void 재발급하면_Refresh_토큰이_바뀌고_한_번_쓴_토큰은_다시_쓸_수_없다() throws Exception {
		insertMember(EMAIL, "USER");
		String first = JsonPath.read(login(LOGIN_BODY).andReturn().getResponse().getContentAsString(),
				"$.data.refreshToken");

		String refreshed = refresh(first)
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andReturn().getResponse().getContentAsString();
		String second = JsonPath.read(refreshed, "$.data.refreshToken");
		assertThat(second).isNotEqualTo(first);

		refresh(first).andExpect(status().isUnauthorized());
		refresh(second).andExpect(status().isOk());
	}

	@Test
	void 로그아웃하면_본문의_Refresh_토큰과_헤더의_Access_토큰을_쓸_수_없다() throws Exception {
		insertMember(EMAIL, "USER");
		String body = login(LOGIN_BODY).andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.data.accessToken");
		String refreshToken = JsonPath.read(body, "$.data.refreshToken");

		mockMvc.perform(post("/api/v1/app/auth/logout").contentType(MediaType.APPLICATION_JSON)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.content(refreshBody(refreshToken)))
				.andExpect(status().isOk());

		refresh(refreshToken).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 브라우저_요청은_앱_인증_API를_쓸_수_없다() throws Exception {
		insertMember(EMAIL, "USER");
		String refreshToken = JsonPath.read(login(LOGIN_BODY).andReturn().getResponse().getContentAsString(),
				"$.data.refreshToken");

		// 웹 프론트 주소에서 온 요청이라도 본문으로 Refresh 토큰을 주지 않는다.
		mockMvc.perform(post("/api/v1/app/auth/login").header("Origin", "http://localhost:3000")
						.contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		for (String path : new String[] { "/api/v1/app/auth/refresh", "/api/v1/app/auth/logout" }) {
			mockMvc.perform(post(path).header("Origin", "http://localhost:3000")
							.contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
					.andExpect(status().isForbidden());
		}
		// 막힌 요청은 토큰을 쓰지 않았으므로 앱은 그대로 재발급할 수 있다.
		refresh(refreshToken).andExpect(status().isOk());
	}

	@Test
	void 관리자_계정은_앱으로_로그인할_수_없고_Refresh_토큰이_없으면_400이다() throws Exception {
		insertMember(EMAIL, "ADMIN");

		login(LOGIN_BODY)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		mockMvc.perform(post("/api/v1/app/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		refresh("not-a-token").andExpect(status().isUnauthorized());
	}

	private ResultActions login(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/app/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/app/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(refreshToken)));
	}

	private static String refreshBody(String refreshToken) {
		return "{\"refreshToken\":\"" + refreshToken + "\"}";
	}

	private void insertMember(String email, String role) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '앱회원', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD), role);
	}
}
