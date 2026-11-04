package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.shopping.domain.audit.repository.AdminAuditLogRepository;
import com.example.shopping.domain.audit.service.AdminAuditLogCleaner;
import com.example.shopping.global.scheduling.ScheduledJobRunner;

/** 보존 기간이 지난 관리자 감사 로그 정리(#67) */
@SpringBootTest
@ActiveProfiles("test")
class AdminAuditLogRetentionTests {

	@Autowired
	private AdminAuditLogCleaner cleaner;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clear() {
		// 테스트는 Flyway 없이 JPA로 스키마를 만들므로 정기 작업 잠금 테이블은 직접 만든다.
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS scheduler_lock (
					lock_name VARCHAR(100) PRIMARY KEY,
					lock_until TIMESTAMP(6) NOT NULL,
					lock_owner VARCHAR(36),
					last_order_id BIGINT NOT NULL DEFAULT 0
				)
				""");
		jdbcTemplate.update("delete from admin_audit_log");
		jdbcTemplate.update("delete from scheduler_lock where lock_name = 'purgeExpiredAuditLogs'");
	}

	@Test
	void 기본_2년이_지난_로그만_나눠서_지운다() {
		LocalDateTime twoYearsAgo = LocalDateTime.now().minusDays(730);
		// 한 번에 지우는 양(1,000건)보다 많이 넣어 여러 번 나눠 지우는지도 본다.
		List<Object[]> expired = new ArrayList<>();
		for (int i = 0; i < 1_200; i++) {
			expired.add(row(twoYearsAgo.minusMinutes(1 + i)));
		}
		insert(expired);
		insert(List.of(row(twoYearsAgo.plusMinutes(10)), row(LocalDateTime.now())));

		cleaner.purgeExpiredAuditLogs();

		assertThat(jdbcTemplate.queryForObject("select count(*) from admin_audit_log", Long.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("select count(*) from admin_audit_log where created_at < ?", Long.class,
				twoYearsAgo)).isZero();
	}

	@Test
	void 보존_기간은_접속기록_보관_기준인_1년보다_짧게_설정할_수_없다() {
		assertThatThrownBy(() -> new AdminAuditLogCleaner(mock(AdminAuditLogRepository.class),
				mock(ScheduledJobRunner.class), 364))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("AUDIT_LOG_RETENTION_DAYS");
	}

	private Object[] row(LocalDateTime createdAt) {
		return new Object[] { 1L, "127.0.0.1", "PATCH", "products", "1",
				"PATCH /api/v1/admin/products/{productId}", 200, createdAt, createdAt };
	}

	private void insert(List<Object[]> rows) {
		jdbcTemplate.batchUpdate("""
				insert into admin_audit_log (actor_member_id, ip_address, action, target_type, target_id, summary,
				    response_status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", rows);
	}
}
