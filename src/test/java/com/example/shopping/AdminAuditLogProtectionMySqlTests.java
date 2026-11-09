package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.shopping.domain.audit.repository.AdminAuditLogRepository;

/**
 * 감사 로그 보호(#131). 앱 계정은 보관 정리(#67) 때문에 감사 로그를 지울 수 있지만, DB 트리거가 고치거나 최근 기록을 지우는 것을 막는다.
 * 트리거는 Flyway가 MySQL에만 만들므로, 운영과 같은 MySQL 8.4에 Flyway 스키마를 만들어 확인한다. Docker가 없으면 건너뛴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class AdminAuditLogProtectionMySqlTests {

	@Container
	private static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.4")
			.withEnv("MYSQL_DATABASE", "shopping")
			.withEnv("MYSQL_USER", "shopping")
			.withEnv("MYSQL_PASSWORD", "shopping")
			.withEnv("MYSQL_ROOT_PASSWORD", "root")
			// RDS 파라미터 그룹과 같게, 바이너리 로그가 켜진 MySQL에서도 마이그레이션 계정이 트리거를 만들 수 있게 한다.
			.withCommand("--log-bin-trust-function-creators=1")
			.withExposedPorts(3306)
			.waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1)
					.withStartupTimeout(Duration.ofMinutes(3)));

	@DynamicPropertySource
	static void mysql(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/shopping");
		registry.add("spring.datasource.username", () -> "shopping");
		registry.add("spring.datasource.password", () -> "shopping");
		// 운영과 같은 스키마(트리거 포함)를 Flyway로 만든다.
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AdminAuditLogRepository repository;

	@Test
	void 감사_로그는_고칠_수_없고_364일보다_최근_기록은_지울_수_없다() {
		long recent = insertLog(LocalDateTime.now().minusDays(363));

		assertThatThrownBy(() -> jdbcTemplate.update("update admin_audit_log set summary = '바꿈' where id = ?", recent))
				.hasMessageContaining("cannot be updated");
		assertThatThrownBy(() -> jdbcTemplate.update("delete from admin_audit_log where id = ?", recent))
				.hasMessageContaining("newer than 364 days");
		assertThat(jdbcTemplate.queryForObject("select summary from admin_audit_log where id = ?", String.class,
				recent)).isEqualTo("테스트 기록");
	}

	@Test
	void 보존_기간이_지난_감사_로그는_정리_작업과_같은_방법으로_지울_수_있다() {
		long expired = insertLog(LocalDateTime.now().minusDays(800));
		long recent = insertLog(LocalDateTime.now().minusDays(10));

		// AdminAuditLogCleaner가 쓰는 조회·삭제(기본 보존 기간 730일)
		List<Long> ids = repository.findIdsCreatedBefore(LocalDateTime.now().minusDays(730), PageRequest.of(0, 1000));
		assertThat(ids).containsExactly(expired);
		assertThat(repository.deleteBatchByIds(ids)).isOne();

		assertThat(jdbcTemplate.queryForList("select id from admin_audit_log", Long.class)).containsExactly(recent);
	}

	private long insertLog(LocalDateTime createdAt) {
		jdbcTemplate.update("""
				insert into admin_audit_log (actor_member_id, ip_address, action, target_type, summary, response_status,
				                             created_at, updated_at)
				values (null, '203.0.113.1', 'CHANGE', 'products', '테스트 기록', 200, ?, ?)
				""", createdAt, createdAt);
		return jdbcTemplate.queryForObject("select max(id) from admin_audit_log", Long.class);
	}
}
