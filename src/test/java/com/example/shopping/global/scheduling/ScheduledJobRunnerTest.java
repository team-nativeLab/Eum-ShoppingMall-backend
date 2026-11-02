package com.example.shopping.global.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.example.shopping.global.monitoring.OperatorAlerts;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ScheduledJobRunnerTest {

	private static final Duration LOCK = Duration.ofMinutes(5);

	private final SchedulerLockService lockService = mock(SchedulerLockService.class);
	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final OperatorAlerts alerts = mock(OperatorAlerts.class);
	private final ScheduledJobRunner runner = new ScheduledJobRunner(lockService, meters, alerts);

	@Test
	void 실패한_작업은_담당자에게_알리고_성공한_작업은_알리지_않는다() {
		when(lockService.tryAcquire(anyString(), any())).thenReturn(Optional.of("owner-1"));
		when(lockService.release(anyString(), anyString())).thenReturn(true);

		runner.run("fine", LOCK, (stats, owner) -> stats.processed());
		verifyNoInteractions(alerts);

		runner.run("items", LOCK, (stats, owner) -> {
			stats.processed();
			stats.failed();
			stats.failed();
		});
		verify(alerts).add(eq("job.failed:items"), contains("처리 1건, 실패 2건"), any());
	}

	@Test
	void 잠금을_잃으면_담당자에게_알린다() {
		when(lockService.tryAcquire(anyString(), any())).thenReturn(Optional.of("owner-1"));
		when(lockService.release(anyString(), anyString())).thenReturn(false);

		runner.run("slow", LOCK, (stats, owner) -> stats.processed());

		verify(alerts).add(eq("job.lock.lost:slow"), contains("slow"), any());
	}

	@Test
	void 실행마다_요청_ID_자리에_작업_ID를_넣고_끝나면_지운다() {
		when(lockService.tryAcquire(anyString(), any())).thenReturn(Optional.of("owner-1"));
		when(lockService.release(anyString(), anyString())).thenReturn(true);
		AtomicReference<String> requestId = new AtomicReference<>();

		runner.run("job", LOCK, (stats, owner) -> requestId.set(MDC.get("requestId")));

		assertThat(requestId.get()).matches("job-[0-9a-f-]{36}");
		assertThat(MDC.get("requestId")).isNull();
	}

	@Test
	void 다른_서버가_잠금을_가지고_있으면_작업을_건너뛴다() {
		when(lockService.tryAcquire("job", LOCK)).thenReturn(Optional.empty());
		AtomicBoolean ran = new AtomicBoolean();

		runner.run("job", LOCK, (stats, owner) -> ran.set(true));

		assertThat(ran).isFalse();
	}

	@Test
	void 항목별_실패와_작업_예외를_지표로_세고_잠금은_항상_반환한다() {
		when(lockService.tryAcquire(anyString(), any())).thenReturn(Optional.of("owner-1"));
		when(lockService.release(anyString(), anyString())).thenReturn(true);

		runner.run("items", LOCK, (stats, owner) -> {
			stats.processed();
			stats.failed();
			stats.failed();
		});
		runner.run("crash", LOCK, (stats, owner) -> {
			throw new IllegalStateException("boom");
		});

		assertThat(failures("items")).isEqualTo(2);
		assertThat(failures("crash")).isEqualTo(1);
		verify(lockService).release("items", "owner-1");
		verify(lockService).release("crash", "owner-1");
	}

	@Test
	void 같은_잠금을_나눠_쓰는_작업은_잠금_이름으로_잡고_잠금을_잃으면_지표를_남긴다() {
		when(lockService.tryAcquire("shared-lock", LOCK)).thenReturn(Optional.of("owner-2"));
		when(lockService.release("shared-lock", "owner-2")).thenReturn(false);

		runner.run("job-a", "shared-lock", LOCK, (stats, owner) -> assertThat(owner).isEqualTo("owner-2"));

		Counter lost = meters.get("scheduled.job.lock.lost").tag("job", "job-a").counter();
		assertThat(lost.count()).isEqualTo(1);
	}

	private double failures(String job) {
		return meters.get("scheduled.job.failures").tag("job", job).counter().count();
	}
}
