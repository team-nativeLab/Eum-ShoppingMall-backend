package com.example.shopping.global.monitoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class OperationalMetricAlertsTest {

	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final OperatorAlerts operatorAlerts = mock(OperatorAlerts.class);
	private final OperationalMetricAlerts alerts = new OperationalMetricAlerts(meters, operatorAlerts);

	@Test
	void 요청이_없으면_알리지_않는다() {
		alerts.check();

		verify(operatorAlerts, never()).add(anyString(), anyString(), any());
	}

	@Test
	void 요청이_충분하고_5xx_비율이_기준을_넘으면_알린다() {
		httpRequests("200", 19);
		httpRequests("500", 1);

		alerts.check();

		verify(operatorAlerts).add(eq("metric:http_5xx"), org.mockito.ArgumentMatchers.contains("20건 중 1건"), any());
	}

	@Test
	void 요청이_적으면_전부_5xx여도_알리지_않는다() {
		httpRequests("503", 19);

		alerts.check();

		verify(operatorAlerts, never()).add(anyString(), anyString(), any());
	}

	@Test
	void 헬스_체크는_요청_수에_넣지_않는다() {
		for (int i = 0; i < 100; i++) {
			meters.timer("http.server.requests", "status", "200", "uri", "/actuator/health/readiness")
					.record(Duration.ofMillis(1));
		}
		httpRequests("500", 19);

		alerts.check();

		verify(operatorAlerts, never()).add(anyString(), anyString(), any());
	}

	@Test
	void 비율이_기준보다_낮으면_알리지_않는다() {
		httpRequests("200", 100);
		httpRequests("500", 4);

		alerts.check();

		verify(operatorAlerts, never()).add(anyString(), anyString(), any());
	}

	@Test
	void 지난번_확인_뒤로_늘어난_요청만_본다() {
		httpRequests("200", 19);
		httpRequests("500", 1);
		alerts.check();

		// 예전 오류가 누적값에 남아 있어도 새 요청이 정상이면 다시 알리지 않는다.
		httpRequests("200", 20);
		alerts.check();

		verify(operatorAlerts, times(1)).add(eq("metric:http_5xx"), anyString(), any());
	}

	@Test
	void DB_커넥션_획득_시간_초과가_있으면_알린다() {
		meters.counter("hikaricp.connections.timeout", "pool", "HikariPool-1").increment();

		alerts.check();

		verify(operatorAlerts).add(eq("metric:db_connection_wait"), org.mockito.ArgumentMatchers.contains("시간 초과 1건"), any());
	}

	@Test
	void DB_커넥션_평균_대기가_길_때만_알린다() {
		meters.timer("hikaricp.connections.acquire", "pool", "HikariPool-1").record(Duration.ofMillis(999));
		alerts.check();
		verify(operatorAlerts, never()).add(anyString(), anyString(), any());

		meters.timer("hikaricp.connections.acquire", "pool", "HikariPool-1").record(Duration.ofSeconds(1));
		alerts.check();
		verify(operatorAlerts).add(eq("metric:db_connection_wait"), org.mockito.ArgumentMatchers.contains("평균 대기 1000ms"), any());
	}

	@Test
	void PG_호출이_충분하고_실패_비율이_기준을_넘으면_알린다() {
		meters.counter("pg.requests", "outcome", "success").increment(4);
		meters.counter("pg.requests", "outcome", "unavailable").increment();

		alerts.check();

		verify(operatorAlerts).add(eq("metric:pg_failure"), org.mockito.ArgumentMatchers.contains("5건 중 1건"), any());
	}

	@Test
	void 카드_거절_같은_업무_거절은_PG_실패로_세지_않는다() {
		meters.counter("pg.requests", "outcome", "rejected").increment(10);

		alerts.check();

		verify(operatorAlerts, never()).add(anyString(), anyString(), any());
	}

	private void httpRequests(String status, int count) {
		for (int i = 0; i < count; i++) {
			meters.timer("http.server.requests", "status", status).record(Duration.ofMillis(1));
		}
	}
}
