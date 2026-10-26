package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.example.shopping.global.scheduling.SchedulerLockService;

class SchedulerLockServiceTests {

	private DataSource dataSource;
	private JdbcTemplate jdbcTemplate;
	private SchedulerLockService schedulerLockService;

	@BeforeEach
	void setUp() {
		dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
				.setName("scheduler-lock-" + UUID.randomUUID()).build();
		jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("""
				CREATE TABLE scheduler_lock (
					lock_name VARCHAR(100) PRIMARY KEY,
					lock_until TIMESTAMP(6) NOT NULL,
					lock_owner VARCHAR(36),
					last_order_id BIGINT NOT NULL DEFAULT 0
				)
				""");
		jdbcTemplate.update("""
				INSERT INTO scheduler_lock (lock_name, lock_until, lock_owner, last_order_id)
				VALUES ('job', DATEADD(SECOND, -1, CURRENT_TIMESTAMP), NULL, 0)
				""");
		schedulerLockService = new SchedulerLockService(jdbcTemplate);
	}

	@AfterEach
	void tearDown() throws Exception {
		((org.springframework.jdbc.datasource.embedded.EmbeddedDatabase) dataSource).shutdown();
	}

	@Test
	void 한_인스턴스가_잠금을_보유하면_다른_인스턴스는_획득하지_못하고_해제_후_다시_획득한다() {
		Optional<String> firstOwner = schedulerLockService.tryAcquire("job", Duration.ofMinutes(1));

		assertThat(firstOwner).isPresent();
		assertThat(schedulerLockService.tryAcquire("job", Duration.ofMinutes(1))).isEmpty();
		assertThat(schedulerLockService.release("job", firstOwner.orElseThrow())).isTrue();
		assertThat(schedulerLockService.tryAcquire("job", Duration.ofMinutes(1))).isPresent();
	}

	@Test
	void 이전_소유자의_해제는_새_잠금과_작업_커서를_변경하지_않는다() {
		String firstOwner = schedulerLockService.tryAcquire("job", Duration.ofMinutes(1)).orElseThrow();
		schedulerLockService.release("job", firstOwner);
		String secondOwner = schedulerLockService.tryAcquire("job", Duration.ofMinutes(1)).orElseThrow();

		assertThat(schedulerLockService.release("job", firstOwner)).isFalse();
		assertThat(schedulerLockService.advanceCursor("job", secondOwner, 42)).isTrue();
		assertThat(schedulerLockService.getCursor("job", secondOwner)).isEqualTo(42);
	}

	@Test
	void 잠금_행이_없는_작업은_처음_쓸_때_행을_만들어_잡는다() {
		Optional<String> owner = schedulerLockService.tryAcquire("new-job", Duration.ofMinutes(1));

		assertThat(owner).isPresent();
		assertThat(schedulerLockService.tryAcquire("new-job", Duration.ofMinutes(1))).isEmpty();
		assertThat(schedulerLockService.getCursor("new-job", owner.orElseThrow())).isZero();
		assertThat(schedulerLockService.release("new-job", owner.orElseThrow())).isTrue();
		assertThat(schedulerLockService.tryAcquire("new-job", Duration.ofMinutes(1))).isPresent();
		assertThat(jdbcTemplate.queryForObject("select count(*) from scheduler_lock where lock_name = 'new-job'",
				Integer.class)).isEqualTo(1);
	}
}
