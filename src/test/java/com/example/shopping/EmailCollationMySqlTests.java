package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 악센트만 다른 이메일(ádmin@…)로 다른 계정에 로그인하거나 로그인 잠금을 우회하는 문제 (#137).
 * H2는 MySQL의 정렬 규칙(utf8mb4_0900_ai_ci)을 따라 하지 않으므로, 운영과 같은 MySQL 8.4에 Flyway 스키마를 만들어 확인한다.
 * Docker가 없으면 건너뛴다. 테스트마다 다른 이메일을 써서 Redis에 남은 실패 횟수와 겹치지 않게 한다.
 */
@SpringBootTest(properties = "security.login-lock.max-failures=10")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithAnonymousUser
@Testcontainers(disabledWithoutDocker = true)
class EmailCollationMySqlTests {

	private static final String PASSWORD = "test-pass-1234";
	private static final String WRONG_PASSWORD = "wrong-pass-1234";

	@Container
	private static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.4")
			.withEnv("MYSQL_DATABASE", "shopping")
			.withEnv("MYSQL_USER", "shopping")
			.withEnv("MYSQL_PASSWORD", "shopping")
			.withEnv("MYSQL_ROOT_PASSWORD", "root")
			.withExposedPorts(3306)
			.waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1)
					.withStartupTimeout(Duration.ofMinutes(3)));

	@DynamicPropertySource
	static void mysql(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/shopping");
		registry.add("spring.datasource.username", () -> "shopping");
		registry.add("spring.datasource.password", () -> "shopping");
		// 운영과 같은 스키마(정렬 규칙 포함)를 Flyway로 만든다.
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void DB는_악센트만_다른_이메일을_같은_회원으로_찾지_않는다() {
		String email = insertMember();

		assertThat(jdbcTemplate.queryForObject("select count(*) from member where email = ?", Long.class,
				accentVariant(email)))
				.as("%s로 %s를 찾음", accentVariant(email), email)
				.isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from member where email = ?", Long.class, email))
				.isOne();
	}

	@Test
	void 값으로_행을_찾는_컬럼은_글자를_그대로_비교한다() {
		List<String> collations = jdbcTemplate.queryForList("""
				select concat(table_name, '.', column_name, '=', collation_name) from information_schema.columns
				where table_schema = database()
				  and (table_name, column_name) in (('member', 'email'), ('email_verification', 'email'),
				                                    ('orders', 'idempotency_key'), ('payment', 'payment_key'))
				""", String.class);

		assertThat(collations).containsExactlyInAnyOrder("member.email=utf8mb4_0900_bin",
				"email_verification.email=utf8mb4_0900_bin", "orders.idempotency_key=utf8mb4_0900_bin",
				"payment.payment_key=utf8mb4_0900_bin");
	}

	@Test
	void 악센트만_다른_이메일로는_다른_계정에_로그인할_수_없다() throws Exception {
		String email = insertMember();

		login(accentVariant(email), PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.data.accessToken").doesNotExist());
		login(email, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 악센트만_다른_이메일로_로그인_잠금을_우회할_수_없다() throws Exception {
		String email = insertMember();
		for (int i = 0; i < 10; i++) {
			login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
		login(email, PASSWORD).andExpect(status().isTooManyRequests());

		login(accentVariant(email), PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.data.accessToken").doesNotExist());
	}

	/** admin-xxxxxxxx@example.com 회원을 만든다. */
	private String insertMember() {
		String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, access_token_version, created_at, updated_at)
				values (?, ?, '관리자', 'ADMIN', 'ACTIVE', 0, now(), now())
				""", email, passwordEncoder.encode(PASSWORD));
		return email;
	}

	/** 첫 글자 a를 á(U+00E1)로 바꾼 이메일 */
	private static String accentVariant(String email) {
		return "á" + email.substring(1);
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}
}
