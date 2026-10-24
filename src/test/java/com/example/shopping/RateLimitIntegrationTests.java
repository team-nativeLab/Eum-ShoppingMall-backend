package com.example.shopping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 요청 제한 필터가 실제 서버 구성에 연결돼 있고 공통 에러 포맷으로 응답하는지 확인한다. */
@SpringBootTest(properties = {
		"security.rate-limit.requests-per-minute=2",
		"spring.datasource.url=jdbc:h2:mem:ratelimit;MODE=MySQL" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 로그인을_분당_한도보다_많이_시도하면_429를_받는다() throws Exception {
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
}
