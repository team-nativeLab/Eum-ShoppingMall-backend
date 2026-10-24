package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.shopping.domain.member.dto.request.AdminMemberStatusUpdateRequest;
import com.example.shopping.domain.member.service.AdminMemberService;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManagerFactory;

/**
 * 실제 Redis를 컨테이너로 띄워, 로그인한 요청이 토큰 검증 때문에 회원 조회 SQL을 더 실행하지 않는지(#71) 확인한다.
 * Docker가 없으면 건너뛴다. 비밀번호 변경·정지가 커밋 직후 반영되는지 보려고 테스트를 트랜잭션으로 묶지 않는다.
 */
@SpringBootTest(properties = { "app.cache.member-token-state-ttl-seconds=300",
		"spring.jpa.properties.hibernate.generate_statistics=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MemberTokenStateCacheIntegrationTests {

	private static final String EMAIL = "cache-member@example.com";
	private static final String ADMIN_EMAIL = "cache-admin@example.com";
	private static final String PASSWORD = "test-pass-1234";

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

	@DynamicPropertySource
	static void redis(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private AdminMemberService adminMemberService;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from refresh_token");
		jdbcTemplate.update("delete from member where email in (?, ?)", EMAIL, ADMIN_EMAIL);
	}

	@Test
	void 실제_토큰으로_요청해도_토큰_검증_때문에_SQL이_늘지_않는다() throws Exception {
		long memberId = insertMember(EMAIL, "USER");
		String token = login(EMAIL, PASSWORD);
		// 첫 요청에서 회원 상태를 Redis에 올린다.
		mockMvc.perform(withToken(get("/api/v1/cart"), token)).andExpect(status().isOk());

		long withToken = countQueries(withToken(get("/api/v1/cart"), token));
		long withoutValidation = countQueries(get("/api/v1/cart")
				.with(jwt().jwt(jwt -> jwt.subject(String.valueOf(memberId)))
						.authorities(new SimpleGrantedAuthority("ROLE_USER"))));

		assertThat(withToken).isEqualTo(withoutValidation);
	}

	@Test
	void 비밀번호를_바꾸면_이전_Access_토큰을_바로_거절한다() throws Exception {
		insertMember(EMAIL, "USER");
		String token = login(EMAIL, PASSWORD);
		mockMvc.perform(withToken(get("/api/v1/cart"), token)).andExpect(status().isOk());

		mockMvc.perform(withToken(patch("/api/v1/me/password"), token).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword":"%s","newPassword":"new-pass-5678"}
						""".formatted(PASSWORD)))
				.andExpect(status().isOk());

		mockMvc.perform(withToken(get("/api/v1/cart"), token)).andExpect(status().isUnauthorized());
		String newToken = login(EMAIL, "new-pass-5678");
		mockMvc.perform(withToken(get("/api/v1/cart"), newToken)).andExpect(status().isOk());
	}

	@Test
	void 관리자가_정지하면_이미_발급된_Access_토큰을_바로_거절하고_해제하면_다시_받는다() throws Exception {
		long memberId = insertMember(EMAIL, "USER");
		long adminId = insertMember(ADMIN_EMAIL, "ADMIN");
		String token = login(EMAIL, PASSWORD);
		mockMvc.perform(withToken(get("/api/v1/cart"), token)).andExpect(status().isOk());

		adminMemberService.changeStatus(adminId, memberId, new AdminMemberStatusUpdateRequest("SUSPENDED", "테스트"));
		mockMvc.perform(withToken(get("/api/v1/cart"), token)).andExpect(status().isUnauthorized());

		adminMemberService.changeStatus(adminId, memberId, new AdminMemberStatusUpdateRequest("ACTIVE", null));
		mockMvc.perform(withToken(get("/api/v1/cart"), token)).andExpect(status().isOk());
	}

	private long countQueries(MockHttpServletRequestBuilder request) throws Exception {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		mockMvc.perform(request).andExpect(status().isOk());
		return statistics.getPrepareStatementCount();
	}

	private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private String login(String email, String password) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.accessToken");
	}

	private long insertMember(String email, String role) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '홍길동', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD), role);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}
}
