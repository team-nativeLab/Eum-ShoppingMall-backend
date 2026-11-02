package com.example.shopping.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ManualCheckAlertsTest {

	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final OperatorAlerts operatorAlerts = mock(OperatorAlerts.class);
	private final ManualCheckAlerts alerts = new ManualCheckAlerts(meters, operatorAlerts);

	@Test
	void 지표를_세고_담당자에게_알린다() {
		alerts.raise("pg_amount_mismatch", "주문 {} 금액이 다릅니다(서버 {} / PG {}).", "ORDER-1", 35000, 100);

		assertThat(meters.counter("manual.check.required", "reason", "pg_amount_mismatch").count()).isEqualTo(1);
		verify(operatorAlerts).add("[수동 확인 필요: pg_amount_mismatch] 주문 ORDER-1 금액이 다릅니다(서버 35000 / PG 100).",
				"[수동 확인 필요: pg_amount_mismatch] 주문 ORDER-1 금액이 다릅니다(서버 35000 / PG 100).", Duration.ofHours(6));
	}

	@Test
	void 예외_내용은_로그에만_남기고_메일에_넣지_않는다() {
		alerts.raise("refund_failed", "결제 {} 환불 실패", "pay-key", new IllegalStateException("민감한 응답 내용"));

		ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
		verify(operatorAlerts).add(message.capture(), message.capture(), org.mockito.ArgumentMatchers.any());
		assertThat(message.getValue()).contains("pay-key").doesNotContain("민감한 응답 내용");
	}
}
