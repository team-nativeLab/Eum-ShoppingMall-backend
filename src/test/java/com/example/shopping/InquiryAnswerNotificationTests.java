package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.shopping.domain.notification.sender.AlimtalkSender;
import com.example.shopping.domain.notification.service.NotificationService;

/**
 * 문의 답변 알림톡 (#22). 답변이 커밋된 뒤 따로 보내는지 보려고 테스트를 트랜잭션으로 묶지 않고 끝나면 지운다.
 * 알림톡 대행사는 가짜로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
@Import(InquiryAnswerNotificationTests.FakeAlimtalk.class)
class InquiryAnswerNotificationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FakeAlimtalkSender alimtalk;

	@Autowired
	private NotificationService notificationService;

	@AfterEach
	void cleanUp() {
		alimtalk.reset();
		jdbcTemplate.update("delete from notification_message");
		jdbcTemplate.update("delete from inquiry where title like '알림 테스트%'");
		jdbcTemplate.update("delete from member where email = 'notify-member@example.com'");
	}

	@Test
	void 답변을_등록하면_접수_때_받은_연락처로_알림톡을_보내고_기록이_남는다() throws Exception {
		long inquiryId = insertInquiry(null, "010-1234-5678");

		answer(inquiryId, "교환은 수령 후 7일 안에 가능합니다.")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ANSWERED"))
				.andExpect(jsonPath("$.data.answerNotification.status").value("PENDING"));

		await().atMost(Duration.ofSeconds(5)).until(() -> "SENT".equals(notificationStatus(inquiryId)));
		assertThat(alimtalk.sent).singleElement().satisfies(message -> {
			assertThat(message.phone()).isEqualTo("01012345678");
			assertThat(message.templateCode()).isEqualTo("INQUIRY_ANSWER");
			assertThat(message.variables()).containsEntry("문의제목", "알림 테스트 교환 문의")
					.containsEntry("답변", "교환은 수령 후 7일 안에 가능합니다.");
		});
		mockMvc.perform(get("/api/v1/admin/inquiries/{id}", inquiryId))
				.andExpect(jsonPath("$.data.answerNotification.status").value("SENT"))
				.andExpect(jsonPath("$.data.answerNotification.attempts").value(1));
	}

	@Test
	void 대행사가_응답하지_않아도_답변_등록은_바로_성공하고_실패한_알림은_3번까지_다시_보낸다() throws Exception {
		alimtalk.failWith(new AlimtalkSender.AlimtalkSendException("timeout", null), Duration.ofSeconds(2));
		long inquiryId = insertInquiry(null, "01012345678");

		long startedAt = System.nanoTime();
		answer(inquiryId, "확인 후 연락드리겠습니다.").andExpect(status().isOk());
		assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(1_500);
		assertThat(jdbcTemplate.queryForObject("select status from inquiry where id = ?", String.class, inquiryId))
				.isEqualTo("ANSWERED");

		await().atMost(Duration.ofSeconds(10)).until(() -> attempts(inquiryId) == 1);
		assertThat(notificationStatus(inquiryId)).isEqualTo("PENDING");
		// 다음 시도 전에는 다시 보내지 않는다.
		assertThat(notificationService.dispatchDue(LocalDateTime.now())).isZero();

		notificationService.dispatchDue(LocalDateTime.now().plusMinutes(2));
		assertThat(attempts(inquiryId)).isEqualTo(2);
		assertThat(notificationStatus(inquiryId)).isEqualTo("PENDING");
		notificationService.dispatchDue(LocalDateTime.now().plusMinutes(10));
		assertThat(attempts(inquiryId)).isEqualTo(3);
		assertThat(notificationStatus(inquiryId)).isEqualTo("FAILED");
		assertThat(jdbcTemplate.queryForObject("select last_error from notification_message", String.class))
				.contains("timeout");

		// 다 실패한 알림은 더 보내지 않는다.
		assertThat(notificationService.dispatchDue(LocalDateTime.now().plusHours(1))).isZero();
		assertThat(alimtalk.calls()).isEqualTo(3);
	}

	@Test
	void 연락처가_없으면_회원_휴대폰_번호로_보내고_휴대폰_번호가_없으면_보내지_않는다() throws Exception {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, phone, role, status, created_at, updated_at)
				values ('notify-member@example.com', 'not-used', '홍길동', '01098765432', 'USER', 'ACTIVE', now(), now())
				""");
		long memberId = jdbcTemplate.queryForObject(
				"select id from member where email = 'notify-member@example.com'", Long.class);
		long memberInquiry = insertInquiry(memberId, null);
		long noPhone = insertInquiry(null, null);
		long landline = insertInquiry(null, "02-123-4567");

		answer(memberInquiry, "답변").andExpect(status().isOk());
		answer(noPhone, "답변").andExpect(status().isOk()).andExpect(jsonPath("$.data.answerNotification").isEmpty());
		answer(landline, "답변").andExpect(status().isOk()).andExpect(jsonPath("$.data.answerNotification").isEmpty());

		await().atMost(Duration.ofSeconds(5)).until(() -> "SENT".equals(notificationStatus(memberInquiry)));
		assertThat(alimtalk.sent).extracting(FakeAlimtalkSender.Sent::phone).containsExactly("01098765432");
		assertThat(jdbcTemplate.queryForObject("select count(*) from notification_message", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void 알림톡_대행사가_설정되지_않으면_보내지_않고_건너뛴_것으로_남긴다() throws Exception {
		alimtalk.configured = false;
		long inquiryId = insertInquiry(null, "01012345678");

		answer(inquiryId, "답변").andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(5)).until(() -> "SKIPPED".equals(notificationStatus(inquiryId)));
		assertThat(alimtalk.calls()).isZero();
	}

	private ResultActions answer(long inquiryId, String answer) throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/inquiries/{id}", inquiryId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"" + answer + "\"}"));
	}

	private long insertInquiry(Long memberId, String contactPhone) {
		String title = memberId == null && contactPhone == null ? "알림 테스트 번호 없음"
				: contactPhone != null && contactPhone.startsWith("02") ? "알림 테스트 일반전화" : "알림 테스트 교환 문의";
		jdbcTemplate.update("""
				insert into inquiry (member_id, contact_phone, inquiry_type, title, content, status, created_at, updated_at)
				values (?, ?, '교환', ?, '내용', 'RECEIVED', now(), now())
				""", memberId, contactPhone, title);
		return jdbcTemplate.queryForObject("select max(id) from inquiry", Long.class);
	}

	private String notificationStatus(long inquiryId) {
		List<String> statuses = jdbcTemplate.queryForList(
				"select status from notification_message where reference_id = ? order by id desc", String.class,
				inquiryId);
		return statuses.isEmpty() ? null : statuses.get(0);
	}

	private int attempts(long inquiryId) {
		return jdbcTemplate.queryForObject("select attempts from notification_message where reference_id = ?",
				Integer.class, inquiryId);
	}

	static class FakeAlimtalkSender implements AlimtalkSender {

		record Sent(String phone, String templateCode, Map<String, String> variables) {
		}

		final List<Sent> sent = new CopyOnWriteArrayList<>();
		volatile boolean configured = true;
		private volatile RuntimeException failure;
		private volatile Duration delay = Duration.ZERO;
		private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

		void failWith(RuntimeException failure, Duration delay) {
			this.failure = failure;
			this.delay = delay;
		}

		int calls() {
			return calls.get();
		}

		void reset() {
			sent.clear();
			calls.set(0);
			configured = true;
			failure = null;
			delay = Duration.ZERO;
		}

		@Override
		public boolean isConfigured() {
			return configured;
		}

		@Override
		public void send(String phoneNumber, String templateCode, Map<String, String> variables) {
			calls.incrementAndGet();
			if (!delay.isZero()) {
				try {
					Thread.sleep(delay.toMillis());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			if (failure != null) {
				throw failure;
			}
			sent.add(new Sent(phoneNumber, templateCode, variables));
		}
	}

	@TestConfiguration
	static class FakeAlimtalk {

		@Bean
		@Primary
		FakeAlimtalkSender fakeAlimtalkSender() {
			return new FakeAlimtalkSender();
		}
	}
}
