package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.privacy.service.PersonalDataRetentionCleaner;
import com.example.shopping.global.scheduling.ScheduledJobRunner;

import io.micrometer.core.instrument.MeterRegistry;

/** 보유 기간이 지난 개인정보를 지운다 (#134). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonalDataRetentionTests {

	private static final String PURGED = "(보유 기간이 지나 삭제됨)";

	@Autowired
	private PersonalDataRetentionCleaner cleaner;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private NamedParameterJdbcTemplate namedJdbcTemplate;

	@Autowired
	private ScheduledJobRunner jobRunner;

	@Autowired
	private MeterRegistry meterRegistry;

	@BeforeEach
	void createLockTable() {
		// 테스트는 Flyway 없이 JPA로 스키마를 만들므로 정기 작업 잠금 테이블은 직접 만든다.
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS scheduler_lock (
					lock_name VARCHAR(100) PRIMARY KEY,
					lock_until TIMESTAMP(6) NOT NULL,
					lock_owner VARCHAR(36),
					last_order_id BIGINT NOT NULL DEFAULT 0
				)
				""");
		jdbcTemplate.update("delete from scheduler_lock where lock_name = 'purgeExpiredPersonalData'");
	}

	@Test
	void 기간이_지난_끝난_주문은_배송지만_지우고_금액과_상태는_남긴다() {
		long memberId = insertMember();
		LocalDateTime sixYearsAgo = LocalDateTime.now().minusYears(6);
		long oldDelivered = insertOrder("ORD-OLD-DELIVERED", memberId, "DELIVERED", sixYearsAgo);
		long oldCanceled = insertOrder("ORD-OLD-CANCELED", memberId, "CANCELED", sixYearsAgo);
		long oldInProgress = insertOrder("ORD-OLD-PAID", memberId, "PAID", sixYearsAgo);
		long recentDelivered = insertOrder("ORD-RECENT", memberId, "DELIVERED", LocalDateTime.now().minusYears(4));
		double before = purgedCount("orders");

		cleaner.purgeExpiredPersonalData();

		for (long id : new long[] { oldDelivered, oldCanceled }) {
			Map<String, Object> row = order(id);
			assertThat(row.get("RECIPIENT")).isNull();
			assertThat(row.get("RECIPIENT_PHONE")).isNull();
			assertThat(row.get("ADDRESS")).isNull();
			assertThat(row.get("DELIVERY_REQUEST")).isNull();
			assertThat(row.get("PERSONAL_DATA_PURGED_AT")).isNotNull();
			assertThat(row.get("TOTAL_AMOUNT")).isEqualTo(35000L);
		}
		// 진행 중인 주문과 기간이 남은 주문은 그대로 둔다.
		assertThat(order(oldInProgress).get("RECIPIENT")).isEqualTo("홍길동");
		assertThat(order(recentDelivered).get("RECIPIENT")).isEqualTo("홍길동");
		// 파기 건수를 지표로 남긴다.
		assertThat(purgedCount("orders") - before).isEqualTo(2);
	}

	@Test
	void 기간이_지난_답변한_문의와_완료한_견적은_개인정보를_지우고_동의_기록은_남긴다() {
		LocalDateTime fourYearsAgo = LocalDateTime.now().minusYears(4);
		long oldAnswered = insertInquiry("ANSWERED", fourYearsAgo);
		long oldReceived = insertInquiry("RECEIVED", fourYearsAgo);
		long recentAnswered = insertInquiry("ANSWERED", LocalDateTime.now().minusYears(2));
		long oldCompleted = insertQuote("COMPLETED", fourYearsAgo);
		long oldInProgress = insertQuote("IN_PROGRESS", fourYearsAgo);

		cleaner.purgeExpiredPersonalData();

		Map<String, Object> inquiry = jdbcTemplate.queryForMap("select * from inquiry where id = ?", oldAnswered);
		assertThat(inquiry.get("CONTACT_PHONE")).isNull();
		assertThat(inquiry.get("CONTACT_EMAIL")).isNull();
		assertThat(inquiry.get("ANSWER")).isNull();
		assertThat(inquiry.get("TITLE")).isEqualTo(PURGED);
		assertThat(inquiry.get("CONTENT")).isEqualTo(PURGED);
		assertThat(inquiry.get("PRIVACY_AGREED_AT")).isNotNull();
		assertThat(inquiry.get("PERSONAL_DATA_PURGED_AT")).isNotNull();
		assertThat(contactEmailOf(oldReceived)).isEqualTo("guest@example.com");
		assertThat(contactEmailOf(recentAnswered)).isEqualTo("guest@example.com");

		Map<String, Object> quote = jdbcTemplate.queryForMap("select * from quote_inquiry where id = ?", oldCompleted);
		assertThat(quote.get("COMPANY_NAME")).isEqualTo("(삭제됨)");
		assertThat(quote.get("MANAGER_NAME")).isEqualTo("(삭제됨)");
		assertThat(quote.get("PHONE")).isEqualTo("(삭제됨)");
		assertThat(quote.get("EMAIL")).isNull();
		assertThat(quote.get("CONTENT")).isEqualTo(PURGED);
		assertThat(quote.get("PRIVACY_AGREED_AT")).isNotNull();
		assertThat(jdbcTemplate.queryForObject("select company_name from quote_inquiry where id = ?", String.class,
				oldInProgress)).isEqualTo("이음산업");
	}

	@Test
	void 보냈거나_최종_실패한_알림은_90일이_지나면_지운다() {
		LocalDateTime longAgo = LocalDateTime.now().minusDays(91);
		long oldSent = insertNotification("SENT", longAgo);
		long oldFailed = insertNotification("FAILED", longAgo);
		long oldPending = insertNotification("PENDING", longAgo);
		long recentSent = insertNotification("SENT", LocalDateTime.now().minusDays(10));

		cleaner.purgeExpiredPersonalData();

		assertThat(jdbcTemplate.queryForList("select id from notification_message order by id", Long.class))
				.containsExactly(oldPending, recentSent)
				.doesNotContain(oldSent, oldFailed);
	}

	@Test
	void 한_번_지운_행은_다시_건드리지_않는다() {
		long memberId = insertMember();
		long id = insertOrder("ORD-ONCE", memberId, "DELIVERED", LocalDateTime.now().minusYears(6));
		cleaner.purgeExpiredPersonalData();
		Object firstPurgedAt = order(id).get("PERSONAL_DATA_PURGED_AT");
		double afterFirst = purgedCount("orders");

		cleaner.purgeExpiredPersonalData();

		assertThat(order(id).get("PERSONAL_DATA_PURGED_AT")).isEqualTo(firstPurgedAt);
		assertThat(purgedCount("orders")).isEqualTo(afterFirst);
	}

	@Test
	void 법정_보관_기간보다_짧은_보유_기간은_받지_않는다() {
		assertThatThrownBy(() -> new PersonalDataRetentionCleaner(jdbcTemplate, namedJdbcTemplate, jobRunner,
				meterRegistry, 365, 1095, 1095, 90)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ORDER_PERSONAL_DATA_RETENTION_DAYS");
		assertThatThrownBy(() -> new PersonalDataRetentionCleaner(jdbcTemplate, namedJdbcTemplate, jobRunner,
				meterRegistry, 1825, 30, 1095, 90)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("INQUIRY_PERSONAL_DATA_RETENTION_DAYS");
	}

	private long insertMember() {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values ('retention@example.com', 'hash', '홍길동', 'USER', 'ACTIVE', now(), now())
				""");
		return jdbcTemplate.queryForObject("select id from member where email = 'retention@example.com'", Long.class);
	}

	private long insertOrder(String orderNo, long memberId, String status, LocalDateTime updatedAt) {
		jdbcTemplate.update("""
				insert into orders (order_no, member_id, status, total_amount, item_amount, discount_amount,
				                    shipping_fee, order_name, recipient, recipient_phone, zip_code, address,
				                    address_detail, delivery_request, expires_at, version, created_at, updated_at)
				values (?, ?, ?, 35000, 30000, 0, 5000, '산업용 안전모', '홍길동', '01012345678', '06236',
				        '서울시 강남구', '101호', '문 앞', ?, 0, ?, ?)
				""", orderNo, memberId, status, updatedAt, updatedAt, updatedAt);
		return jdbcTemplate.queryForObject("select id from orders where order_no = ?", Long.class, orderNo);
	}

	private Map<String, Object> order(long id) {
		return jdbcTemplate.queryForMap("select * from orders where id = ?", id);
	}

	private long insertInquiry(String status, LocalDateTime updatedAt) {
		jdbcTemplate.update("""
				insert into inquiry (member_id, contact_phone, contact_email, inquiry_type, title, content, answer,
				                     status, privacy_agreed_at, created_at, updated_at)
				values (null, '01012345678', 'guest@example.com', '배송', '배송 문의', '홍길동입니다. 언제 오나요?',
				        '홍길동 고객님, 내일 도착합니다.', ?, ?, ?, ?)
				""", status, updatedAt, updatedAt, updatedAt);
		return jdbcTemplate.queryForObject("select max(id) from inquiry", Long.class);
	}

	private String contactEmailOf(long inquiryId) {
		return jdbcTemplate.queryForObject("select contact_email from inquiry where id = ?", String.class, inquiryId);
	}

	private long insertQuote(String status, LocalDateTime updatedAt) {
		jdbcTemplate.update("""
				insert into quote_inquiry (company_name, manager_name, phone, email, content, status,
				                           privacy_agreed_at, created_at, updated_at)
				values ('이음산업', '김담당', '0212345678', 'buyer@example.com', '안전모 100개 견적', ?, ?, ?, ?)
				""", status, updatedAt, updatedAt, updatedAt);
		return jdbcTemplate.queryForObject("select max(id) from quote_inquiry", Long.class);
	}

	private long insertNotification(String status, LocalDateTime updatedAt) {
		jdbcTemplate.update("""
				insert into notification_message (channel, template_code, recipient, variables, reference_type,
				                                  reference_id, status, attempts, next_attempt_at, created_at, updated_at)
				values ('ALIMTALK', 'INQUIRY_ANSWERED', '01012345678', '{}', 'INQUIRY', 1, ?, 1, ?, ?, ?)
				""", status, updatedAt, updatedAt, updatedAt);
		return jdbcTemplate.queryForObject("select max(id) from notification_message", Long.class);
	}

	private double purgedCount(String type) {
		var counter = meterRegistry.find("personal.data.purged").tag("type", type).counter();
		return counter == null ? 0 : counter.count();
	}
}
