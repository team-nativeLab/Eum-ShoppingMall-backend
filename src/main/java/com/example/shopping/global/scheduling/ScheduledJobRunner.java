package com.example.shopping.global.scheduling;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정기 작업을 DB 잠금으로 한 서버에서만 실행하고 결과를 같은 형식으로 남긴다.
 * - 다른 서버가 실행 중이면 건너뛴다.
 * - 작업이 던진 예외와 항목별 실패는 scheduled.job.failures{job} 지표로 센다.
 * - 끝났을 때 잠금이 이미 만료돼 다른 서버가 가져갔으면 오류 로그와 scheduled.job.lock.lost{job} 지표를 남긴다.
 * 처리하거나 실패한 것이 있을 때만 완료 로그를 info로 남겨, 1분마다 도는 작업이 로그를 채우지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobRunner {

	private final SchedulerLockService schedulerLockService;
	private final MeterRegistry meterRegistry;

	public void run(String jobName, Duration lockDuration, Job job) {
		run(jobName, jobName, lockDuration, job);
	}

	/** 같은 대상을 다루는 작업끼리 lockName을 같게 주면 동시에 돌지 않는다. */
	public void run(String jobName, String lockName, Duration lockDuration, Job job) {
		Optional<String> owner = schedulerLockService.tryAcquire(lockName, lockDuration);
		if (owner.isEmpty()) {
			log.debug("정기 작업 {} 생략: 다른 서버가 실행 중입니다.", jobName);
			return;
		}
		JobStats stats = new JobStats();
		try {
			job.run(stats, owner.get());
		} catch (RuntimeException e) {
			stats.failed();
			log.error("정기 작업 {} 실행 실패", jobName, e);
		} finally {
			if (!schedulerLockService.release(lockName, owner.get())) {
				meterRegistry.counter("scheduled.job.lock.lost", "job", jobName).increment();
				log.error("정기 작업 {} 잠금이 만료되었거나 소유권을 잃었습니다.", jobName);
			}
			if (stats.failed > 0) {
				meterRegistry.counter("scheduled.job.failures", "job", jobName).increment(stats.failed);
			}
			long elapsedMs = Duration.ofNanos(System.nanoTime() - stats.startedAtNanos).toMillis();
			if (stats.processed > 0 || stats.failed > 0) {
				log.info("정기 작업 {} 완료: 처리 {}, 실패 {}, 소요 {}ms", jobName, stats.processed, stats.failed, elapsedMs);
			} else {
				log.debug("정기 작업 {} 완료: 처리할 대상 없음, 소요 {}ms", jobName, elapsedMs);
			}
		}
	}

	@FunctionalInterface
	public interface Job {
		/** owner는 이 실행이 가진 잠금의 ID다. 잠금 행에 커서를 저장할 때 쓴다. */
		void run(JobStats stats, String owner);
	}

	/** 처리·실패 건수. 항목마다 PG를 부르는 작업은 timeLimitReached로 잠금 시간 안에 끊는다. */
	public static final class JobStats {

		private final long startedAtNanos = System.nanoTime();
		private int processed;
		private int failed;

		public void processed() {
			processed++;
		}

		public void processed(int count) {
			processed += count;
		}

		public void failed() {
			failed++;
		}

		public int processedCount() {
			return processed;
		}

		public boolean timeLimitReached(Duration limit) {
			return System.nanoTime() - startedAtNanos >= limit.toNanos();
		}
	}
}
