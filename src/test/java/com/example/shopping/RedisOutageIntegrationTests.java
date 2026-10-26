package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.security.AccessTokenStateStore;
import com.example.shopping.global.security.RedisAccessTokenStateStore;
import com.jayway.jsonpath.JsonPath;

/**
 * Redis 컨테이너를 일시 정지해 "연결은 있지만 응답하지 않는" 장애를 만들고 서비스가 계속 응답하는지 확인한다 (#69, #70).
 * Docker가 없으면 건너뛴다. 운영처럼 Redis를 쓰는 기능(요청 제한, 상품 캐시, 회원 상태 캐시, 토큰 차단)을 모두 켠다.
 */
@SpringBootTest(properties = { "app.cache.product-ttl-seconds=60", "app.cache.member-token-state-ttl-seconds=300",
		"management.health.redis.enabled=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(RedisOutageIntegrationTests.RedisTokenStore.class)
class RedisOutageIntegrationTests {

	private static final String MEMBER_EMAIL = "outage-member@example.com";
	private static final String ADMIN_EMAIL = "outage-admin@example.com";
	private static final String PASSWORD = "test-pass-1234";

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

	@DynamicPropertySource
	static void redis(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}

	/** 테스트 프로파일의 메모리 저장소 대신 운영과 같은 Redis 토큰 저장소를 쓴다. */
	@TestConfiguration
	static class RedisTokenStore {

		@Bean
		@Primary
		AccessTokenStateStore redisAccessTokenStateStore(StringRedisTemplate redis, RedisCircuitBreaker breaker) {
			return new RedisAccessTokenStateStore(redis, breaker);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@AfterEach
	void cleanUp() {
		if (REDIS.getDockerClient().inspectContainerCmd(REDIS.getContainerId()).exec().getState().getPaused()) {
			REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
		}
		jdbcTemplate.update("delete from refresh_token");
		jdbcTemplate.update("delete from member where email in (?, ?)", MEMBER_EMAIL, ADMIN_EMAIL);
	}

	@Test
	void Redis가_응답을_멈춰도_조회와_로그인한_회원의_요청은_빠르게_응답하고_관리자는_503을_받는다() throws Exception {
		insertMember(MEMBER_EMAIL, "USER");
		insertMember(ADMIN_EMAIL, "ADMIN");
		String memberToken = login("/api/v1/auth/login", MEMBER_EMAIL);
		String adminToken = login("/api/v1/admin/auth/login", ADMIN_EMAIL);
		mockMvc.perform(withToken(get("/api/v1/admin/orders"), adminToken)).andExpect(status().isOk());

		REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();

		// 처음 몇 번은 Redis 명령 대기 시간(0.5초)만큼 기다린 뒤 대체 동작으로 응답한다.
		mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
		mockMvc.perform(withToken(get("/api/v1/cart"), memberToken)).andExpect(status().isOk());

		// 실패가 이어지면 Redis를 잠시 건너뛰므로 기다리지 않는다. (건너뛰지 않으면 요청마다 1초 이상 걸린다.)
		long startedAt = System.nanoTime();
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/products/suggestions").param("query", "안전")).andExpect(status().isOk());
			mockMvc.perform(withToken(get("/api/v1/cart"), memberToken)).andExpect(status().isOk());
		}
		assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(3_000);

		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
		// 관리자 토큰은 로그아웃 여부를 확인할 수 없으면 받지 않는다. 로그아웃시키지 않도록 401이 아니라 503으로 알린다.
		mockMvc.perform(withToken(get("/api/v1/admin/orders"), adminToken))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

		REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();

		// Redis가 돌아오면 잠시 뒤(최대 10초) 관리자 요청도 다시 받는다.
		int adminStatus = 0;
		for (int attempt = 0; attempt < 30 && adminStatus != 200; attempt++) {
			adminStatus = mockMvc.perform(withToken(get("/api/v1/admin/orders"), adminToken))
					.andReturn().getResponse().getStatus();
			if (adminStatus != 200) {
				Thread.sleep(1_000);
			}
		}
		assertThat(adminStatus).isEqualTo(200);
	}

	@Test
	void Redis가_응답을_멈춰도_로그아웃은_성공하고_그_토큰은_이_서버에서_거절된다() throws Exception {
		insertMember(MEMBER_EMAIL, "USER");
		String token = login("/api/v1/auth/login", MEMBER_EMAIL);

		REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();

		mockMvc.perform(withToken(post("/api/v1/auth/logout"), token)).andExpect(status().isOk());
		mockMvc.perform(withToken(get("/api/v1/cart"), token)).andExpect(status().isUnauthorized());
	}

	private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private String login(String path, String email) throws Exception {
		String body = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.accessToken");
	}

	private void insertMember(String email, String role) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '홍길동', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD), role);
	}
}
