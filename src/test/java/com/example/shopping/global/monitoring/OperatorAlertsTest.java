package com.example.shopping.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.example.shopping.global.mail.EmailSender;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class OperatorAlertsTest {

	private static final Duration REPEAT = Duration.ofHours(1);

	private final MutableClock clock = new MutableClock();
	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final FakeEmailSender sender = new FakeEmailSender();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void 쌓인_알림을_한_통으로_묶어_모든_수신자에게_보낸다() {
		OperatorAlerts alerts = alerts(" ops@eum.test", "dev@eum.test ", " ");

		alerts.add("a", "첫 번째 알림", REPEAT);
		alerts.add("b", "두 번째 알림", REPEAT);
		alerts.sendPending();

		assertThat(sender.mails).extracting(Mail::to).containsExactly("ops@eum.test", "dev@eum.test");
		assertThat(sender.mails.get(0).subject()).isEqualTo("[이음] 담당자 확인 필요 2건");
		assertThat(sender.mails.get(0).text()).contains("첫 번째 알림").contains("두 번째 알림");

		// 이미 보낸 알림은 다시 보내지 않는다.
		alerts.sendPending();
		assertThat(sender.mails).hasSize(2);
	}

	@Test
	void 같은_키는_다시_알릴_간격이_지나기_전까지_보내지_않는다() {
		OperatorAlerts alerts = alerts("ops@eum.test");

		alerts.add("same", "같은 문제", REPEAT);
		alerts.add("same", "같은 문제", REPEAT);
		alerts.sendPending();
		assertThat(sender.mails).hasSize(1);
		assertThat(sender.mails.get(0).subject()).endsWith("1건");

		clock.advance(REPEAT.minusSeconds(1));
		alerts.add("same", "같은 문제", REPEAT);
		alerts.sendPending();
		assertThat(sender.mails).hasSize(1);

		clock.advance(Duration.ofSeconds(1));
		alerts.add("same", "같은 문제", REPEAT);
		alerts.sendPending();
		assertThat(sender.mails).hasSize(2);
	}

	@Test
	void 수신자가_없으면_보내지_않는다() {
		OperatorAlerts alerts = alerts("");

		alerts.add("a", "알림", REPEAT);
		alerts.sendPending();

		assertThat(sender.mails).isEmpty();
	}

	@Test
	void 메일_서버_장애로_아무도_받지_못하면_다음_차례에_다시_보낸다() {
		OperatorAlerts alerts = alerts("ops@eum.test", "dev@eum.test");
		alerts.add("a", "알림", REPEAT);

		sender.failFor = Set.of("ops@eum.test", "dev@eum.test");
		alerts.sendPending();
		assertThat(sender.mails).isEmpty();
		assertThat(meters.counter("operator.alert.delivery.failures").count()).isEqualTo(2);

		sender.failFor = Set.of();
		alerts.sendPending();
		assertThat(sender.mails).hasSize(2);
	}

	@Test
	void 한_명이라도_받았으면_다시_보내지_않는다() {
		OperatorAlerts alerts = alerts("ops@eum.test", "dev@eum.test");
		alerts.add("a", "알림", REPEAT);

		sender.failFor = Set.of("dev@eum.test");
		alerts.sendPending();
		sender.failFor = Set.of();
		alerts.sendPending();

		assertThat(sender.mails).extracting(Mail::to).containsExactly("ops@eum.test");
		assertThat(meters.counter("operator.alert.delivery.failures").count()).isEqualTo(1);
	}

	@Test
	void 너무_많이_쌓이면_넘친_건수를_함께_알린다() {
		OperatorAlerts alerts = alerts("ops@eum.test");

		for (int i = 0; i < 103; i++) {
			alerts.add("key-" + i, "알림 " + i, REPEAT);
		}
		alerts.sendPending();

		assertThat(sender.mails.get(0).subject()).endsWith("103건");
		assertThat(sender.mails.get(0).text()).contains("알림 99").doesNotContain("알림 100").contains("이 밖에 3건");
	}

	@Test
	void 요청_ID를_함께_보내_로그를_찾을_수_있게_한다() {
		OperatorAlerts alerts = alerts("ops@eum.test");

		MDC.put("requestId", "req-1");
		alerts.add("a", "알림", REPEAT);
		alerts.sendPending();

		assertThat(sender.mails.get(0).text()).contains("알림 (requestId=req-1)");
	}

	@Test
	void 서버가_내려갈_때_쌓인_알림을_보낸다() {
		OperatorAlerts alerts = alerts("ops@eum.test");

		alerts.add("a", "알림", REPEAT);
		alerts.destroy();

		assertThat(sender.mails).hasSize(1);
	}

	private OperatorAlerts alerts(String... recipients) {
		return new OperatorAlerts(sender, meters, List.of(recipients), "이음", clock);
	}

	private record Mail(String to, String subject, String text) {
	}

	private static final class FakeEmailSender implements EmailSender {
		private final List<Mail> mails = new ArrayList<>();
		private Set<String> failFor = Set.of();

		@Override
		public void send(String to, String subject, String text) {
			if (failFor.contains(to)) {
				throw new EmailSendException("메일 서버 장애", null);
			}
			mails.add(new Mail(to, subject, text));
		}
	}

	private static final class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-11-02T01:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("Asia/Seoul");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
