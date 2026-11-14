package com.example.shopping.domain.audit.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.example.shopping.domain.audit.repository.AdminAuditLogRepository;
import com.example.shopping.global.scheduling.ScheduledJobRunner;

/**
 * 보존 기간(app.audit.retention-days, 기본 2년)이 지난 관리자 감사 로그를 지운다(#67).
 * 개인정보 처리시스템 접속기록 보관 기준(1년 이상, 일정 규모 이상은 2년 이상)보다 짧게는 설정할 수 없다.
 * DB를 오래 잠그지 않도록 1,000건씩 나눠 지우고 한 번에 5분까지만 돈다. 남은 로그는 다음 날 이어서 지운다.
 */
@Component
public class AdminAuditLogCleaner {

	static final int MIN_RETENTION_DAYS = 365;
	private static final String JOB_NAME = "purgeExpiredAuditLogs";
	private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
	private static final Duration TIME_LIMIT = Duration.ofMinutes(5);
	private static final int BATCH_SIZE = 1000;

	private final AdminAuditLogRepository repository;
	private final ScheduledJobRunner jobRunner;
	private final int retentionDays;

	public AdminAuditLogCleaner(AdminAuditLogRepository repository, ScheduledJobRunner jobRunner,
			@Value("${app.audit.retention-days:730}") int retentionDays) {
		Assert.isTrue(retentionDays >= MIN_RETENTION_DAYS,
				"AUDIT_LOG_RETENTION_DAYS는 " + MIN_RETENTION_DAYS + " 이상이어야 합니다. (접속기록 보관 기준)");
		this.repository = repository;
		this.jobRunner = jobRunner;
		this.retentionDays = retentionDays;
	}

	@Scheduled(cron = "0 15 5 * * *")
	public void purgeExpiredAuditLogs() {
		jobRunner.run(JOB_NAME, LOCK_DURATION, (stats, owner) -> {
			LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
			while (!stats.timeLimitReached(TIME_LIMIT)) {
				List<Long> ids = repository.findIdsCreatedBefore(cutoff, PageRequest.of(0, BATCH_SIZE));
				if (ids.isEmpty()) {
					return;
				}
				stats.processed(repository.deleteBatchByIds(ids));
			}
		});
	}
}
