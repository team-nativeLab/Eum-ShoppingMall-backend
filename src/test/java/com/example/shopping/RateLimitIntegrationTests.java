package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** 요청 제한 필터가 실제 서버 구성에 연결돼 있고 공통 에러 포맷으로 응답하는지 확인한다. */
@SpringBootTest(properties = {
		"security.rate-limit.requests-per-minute=2",
		"spring.datasource.url=jdbc:h2:mem:ratelimit;MODE=MySQL" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void 로그인을_분당_한도보다_많이_시도하면_429를_받는다() throws Exception {
		avoidMinuteBoundary();
		String body = """
				{"email":"nobody@example.com","password":"wrong-pass-1234"}
				""";
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));

		// 조회 API는 제한 대상이 아니다.
		mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
	}

	@Test
	void 회원과_관리자_로그인_한도를_우회하려_경로를_바꿔도_같은_IP는_429를_받는다() throws Exception {
		avoidMinuteBoundary();
		String body = """
				{"email":"nobody@example.com","password":"wrong-pass-1234"}
				""";
		String remoteAddress = "192.0.2.42";

		mockMvc.perform(post("/api/v1/auth/login").with(request -> {
			request.setRemoteAddr(remoteAddress);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/admin/auth/login").with(request -> {
			request.setRemoteAddr(remoteAddress);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/login").with(request -> {
			request.setRemoteAddr(remoteAddress);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
	}

	@Test
	void 비밀번호_변경과_회원_탈퇴를_번갈아_써도_비밀번호_확인_한도를_함께_센다() throws Exception {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values ('withdraw-limit@example.com', ?, '홍길동', 'USER', 'ACTIVE', now(), now())
				""", passwordEncoder.encode("test-pass-1234"));
		// 다른 테스트가 기본 IP의 로그인 한도를 쓰므로 로그인도 다른 IP에서 한다.
		String token = JsonPath.read(mockMvc.perform(post("/api/v1/auth/login").with(r -> {
					r.setRemoteAddr("192.0.2.61");
					return r;
				}).contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"withdraw-limit@example.com\",\"password\":\"test-pass-1234\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.accessToken");
		avoidMinuteBoundary();

		// 탈퇴 API도 틀린 비밀번호를 한도까지만 받는다(#162).
		mockMvc.perform(fromAddress(delete("/api/v1/me"), token).content("{\"password\":\"wrong-pass-0001\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
		mockMvc.perform(fromAddress(patch("/api/v1/me/password"), token)
						.content("{\"currentPassword\":\"wrong-pass-0002\",\"newPassword\":\"new-pass-5678\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(fromAddress(delete("/api/v1/me"), token).content("{\"password\":\"wrong-pass-0003\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
		assertThat(jdbcTemplate.queryForObject("select status from member where email = 'withdraw-limit@example.com'",
				String.class)).isEqualTo("ACTIVE");
	}

	private static MockHttpServletRequestBuilder fromAddress(MockHttpServletRequestBuilder request, String token) {
		return request.with(r -> {
			r.setRemoteAddr("192.0.2.62");
			return r;
		}).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
	}

	/** 요청 제한은 분 단위로 센다. 요청들이 분 경계에 걸치면 횟수가 0부터 다시 세어지므로, 경계 직전이면 다음 분까지 기다린다. */
	private static void avoidMinuteBoundary() throws InterruptedException {
		long millisIntoMinute = System.currentTimeMillis() % 60_000;
		if (millisIntoMinute > 50_000) {
			Thread.sleep(60_000 - millisIntoMinute + 100);
		}
	}
}
