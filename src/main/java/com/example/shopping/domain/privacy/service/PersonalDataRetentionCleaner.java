package com.example.shopping.domain.privacy.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.example.shopping.global.scheduling.ScheduledJobRunner;
import com.example.shopping.global.scheduling.ScheduledJobRunner.JobStats;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 보유 기간이 지난 개인정보를 지운다(#134, 개인정보 보호법 제21조). 기간은 개인정보 처리방침의 보유 기간과 맞춘다.
 * <ul>
 * <li>주문(배송·취소·만료가 끝난 것): 받는 사람·연락처·주소·배송 요청만 지운다. 금액·품목·상태는 매출 통계에 쓰므로 남긴다.
 * 기본 5년(전자상거래법의 계약·대금결제·재화 공급 기록).</li>
 * <li>1:1 문의(답변한 것): 연락처·이메일·제목·내용·답변을 지운다. 기본 3년(소비자 불만·분쟁 처리 기록).</li>
 * <li>견적 문의(완료한 것): 회사명·담당자·연락처·이메일·내용을 지운다. 기본 3년.</li>
 * <li>알림 발송 기록(보냈거나 최종 실패한 것): 행을 지운다. 기본 90일.</li>
 * </ul>
 * 진행 중인 주문, 답변 전 문의, 끝나지 않은 견적은 기간이 지나도 두고, 동의 시각(privacy_agreed_at)은 분쟁 근거이므로 남긴다(#21).
 * 지운 행은 personal_data_purged_at을 채워 다시 읽지 않는다. DB를 오래 잠그지 않도록 500건씩 나눠 처리하고 한 번에 5분까지만 돈다.
 * 종류별 처리 건수를 personal.data.purged{type} 지표로 남긴다(파기 기록). 실패하면 정기 작업 실패 알림이 간다(#32).
 */
@Slf4j
@Component
public class PersonalDataRetentionCleaner {

	static final String PURGED_TEXT = "(보유 기간이 지나 삭제됨)";
	static final int MIN_ORDER_DAYS = 1825;
	static final int MIN_INQUIRY_DAYS = 1095;
	private static final String JOB_NAME = "purgeExpiredPersonalData";
	private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
	private static final Duration TIME_LIMIT = Duration.ofMinutes(5);
	private static final int BATCH_SIZE = 500;

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	private final ScheduledJobRunner jobRunner;
	private final MeterRegistry meterRegistry;
	private final int orderDays;
	private final int inquiryDays;
	private final int quoteDays;
	private final int notificationDays;

	public PersonalDataRetentionCleaner(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate,
			ScheduledJobRunner jobRunner, MeterRegistry meterRegistry,
			@Value("${app.retention.order-days:1825}") int orderDays,
			@Value("${app.retention.inquiry-days:1095}") int inquiryDays,
			@Value("${app.retention.quote-days:1095}") int quoteDays,
			@Value("${app.retention.notification-days:90}") int notificationDays) {
		Assert.isTrue(orderDays >= MIN_ORDER_DAYS,
				"ORDER_PERSONAL_DATA_RETENTION_DAYS는 " + MIN_ORDER_DAYS + " 이상이어야 합니다. (전자상거래법 거래 기록 5년)");
		Assert.isTrue(inquiryDays >= MIN_INQUIRY_DAYS,
				"INQUIRY_PERSONAL_DATA_RETENTION_DAYS는 " + MIN_INQUIRY_DAYS + " 이상이어야 합니다. (소비자 분쟁 처리 기록 3년)");
		Assert.isTrue(quoteDays > 0 && notificationDays > 0, "보유 기간은 1일 이상이어야 합니다.");
		this.jdbcTemplate = jdbcTemplate;
		this.namedJdbcTemplate = namedJdbcTemplate;
		this.jobRunner = jobRunner;
		this.meterRegistry = meterRegistry;
		this.orderDays = orderDays;
		this.inquiryDays = inquiryDays;
		this.quoteDays = quoteDays;
		this.notificationDays = notificationDays;
	}

	@Scheduled(cron = "0 30 5 * * *")
	public void purgeExpiredPersonalData() {
		jobRunner.run(JOB_NAME, LOCK_DURATION, this::purge);
	}

	void purge(JobStats stats, String owner) {
		LocalDateTime now = LocalDateTime.now();
		purgeBatches(stats, "orders", """
				select id from orders
				where personal_data_purged_at is null and status in ('DELIVERED', 'CANCELED', 'EXPIRED') and updated_at < ?
				order by id limit ?
				""", now.minusDays(orderDays), """
				update orders
				set recipient = null, recipient_phone = null, zip_code = null, address = null, address_detail = null,
				    delivery_request = null, personal_data_purged_at = :now
				where id in (:ids) and personal_data_purged_at is null
				""", now);
		purgeBatches(stats, "inquiry", """
				select id from inquiry
				where personal_data_purged_at is null and status = 'ANSWERED' and updated_at < ?
				order by id limit ?
				""", now.minusDays(inquiryDays), """
				update inquiry
				set contact_phone = null, contact_email = null, title = :purged, content = :purged, answer = null,
				    personal_data_purged_at = :now
				where id in (:ids) and personal_data_purged_at is null
				""", now);
		purgeBatches(stats, "quote_inquiry", """
				select id from quote_inquiry
				where personal_data_purged_at is null and status = 'COMPLETED' and updated_at < ?
				order by id limit ?
				""", now.minusDays(quoteDays), """
				update quote_inquiry
				set company_name = :purgedShort, manager_name = :purgedShort, phone = :purgedShort, email = null,
				    content = :purged, personal_data_purged_at = :now
				where id in (:ids) and personal_data_purged_at is null
				""", now);
		purgeBatches(stats, "notification_message", """
				select id from notification_message
				where status in ('SENT', 'FAILED', 'SKIPPED') and updated_at < ?
				order by id limit ?
				""", now.minusDays(notificationDays), """
				delete from notification_message where id in (:ids)
				""", now);
	}

	private void purgeBatches(JobStats stats, String type, String selectSql, LocalDateTime cutoff, String purgeSql,
			LocalDateTime now) {
		int purged = 0;
		while (!stats.timeLimitReached(TIME_LIMIT)) {
			List<Long> ids = jdbcTemplate.queryForList(selectSql, Long.class, cutoff, BATCH_SIZE);
			if (ids.isEmpty()) {
				break;
			}
			int count = namedJdbcTemplate.update(purgeSql, Map.of("ids", ids, "now", now, "purged", PURGED_TEXT,
					"purgedShort", "(삭제됨)"));
			purged += count;
			stats.processed(count);
		}
		if (purged > 0) {
			meterRegistry.counter("personal.data.purged", "type", type).increment(purged);
			log.info("보유 기간이 지난 개인정보를 지웠습니다: type={}, count={}", type, purged);
		}
	}
}
