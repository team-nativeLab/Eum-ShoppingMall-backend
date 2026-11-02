package com.example.shopping.global.monitoring;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

/**
 * 5xx 비율, DB 커넥션 대기, PG 실패 비율을 5분마다 직접 확인해 기준을 넘으면 담당자에게 알린다.
 * 지표 수집 도구 없이 앱만으로 알림을 받기 위해서다. 서버별 수치이므로 서버가 여러 대면 서버마다 따로 판단한다.
 */
@Component
@RequiredArgsConstructor
public class OperationalMetricAlerts {

	private static final Duration REPEAT_AFTER = Duration.ofHours(1);
	// 요청이 적을 때 한두 건의 오류로 알림이 울리지 않게 최소 건수도 본다.
	private static final int HTTP_MIN_REQUESTS = 20;
	private static final double HTTP_MAX_5XX_RATIO = 0.05;
	private static final int PG_MIN_REQUESTS = 5;
	private static final double PG_MAX_FAILURE_RATIO = 0.2;
	private static final double DB_MAX_AVERAGE_WAIT_MILLIS = 1000;

	private final MeterRegistry meterRegistry;
	private final OperatorAlerts operatorAlerts;
	private final Map<String, Double> lastTotals = new HashMap<>();

	@Scheduled(fixedDelay = 300_000)
	public void check() {
		checkHttpErrors();
		checkDbConnectionWait();
		checkPgFailures();
	}

	private void checkHttpErrors() {
		// 로드밸런서 헬스 체크(/actuator)가 5분에 수십 건이라 넣으면 최소 건수와 비율이 의미를 잃는다.
		List<Timer> timers = meterRegistry.find("http.server.requests").timers().stream()
				.filter(timer -> !String.valueOf(timer.getId().getTag("uri")).startsWith("/actuator"))
				.toList();
		double requests = increase("http.requests", timers.stream().mapToDouble(Timer::count).sum());
		double errors = increase("http.5xx", timers.stream()
				.filter(timer -> String.valueOf(timer.getId().getTag("status")).startsWith("5"))
				.mapToDouble(Timer::count).sum());
		if (requests >= HTTP_MIN_REQUESTS && errors / requests >= HTTP_MAX_5XX_RATIO) {
			alert("http_5xx", "최근 5분 동안 5xx 응답이 많습니다: %.0f건 중 %.0f건".formatted(requests, errors));
		}
	}

	private void checkDbConnectionWait() {
		double timeouts = increase("db.timeouts", meterRegistry.find("hikaricp.connections.timeout").counters().stream()
				.mapToDouble(Counter::count).sum());
		Collection<Timer> acquires = meterRegistry.find("hikaricp.connections.acquire").timers();
		double count = increase("db.acquires", acquires.stream().mapToDouble(Timer::count).sum());
		double waitMillis = increase("db.wait", acquires.stream()
				.mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS)).sum());
		double averageWaitMillis = count > 0 ? waitMillis / count : 0;
		if (timeouts > 0 || averageWaitMillis >= DB_MAX_AVERAGE_WAIT_MILLIS) {
			alert("db_connection_wait", "최근 5분 동안 DB 커넥션 대기가 깁니다: 시간 초과 %.0f건, 평균 대기 %.0fms"
					.formatted(timeouts, averageWaitMillis));
		}
	}

	private void checkPgFailures() {
		Collection<Counter> counters = meterRegistry.find("pg.requests").counters();
		double requests = increase("pg.requests", counters.stream().mapToDouble(Counter::count).sum());
		double failures = increase("pg.failures", counters.stream()
				.filter(counter -> "unavailable".equals(counter.getId().getTag("outcome")))
				.mapToDouble(Counter::count).sum());
		if (requests >= PG_MIN_REQUESTS && failures / requests >= PG_MAX_FAILURE_RATIO) {
			alert("pg_failure", "최근 5분 동안 PG 호출 실패가 많습니다: %.0f건 중 %.0f건".formatted(requests, failures));
		}
	}

	private void alert(String name, String message) {
		operatorAlerts.add("metric:" + name, "[운영 지표: " + name + "] " + message, REPEAT_AFTER);
	}

	/** 누적값이 지난번 확인 뒤로 얼마나 늘었는지 돌려준다. */
	private double increase(String name, double total) {
		Double last = lastTotals.put(name, total);
		return last == null ? total : total - last;
	}
}
