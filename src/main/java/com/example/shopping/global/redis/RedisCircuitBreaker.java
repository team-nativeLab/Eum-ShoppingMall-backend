package com.example.shopping.global.redis;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

/**
 * Redis 호출이 연달아 실패하면 잠시 Redis를 부르지 않는다.
 * Redis가 응답을 멈추면 호출마다 명령 대기 시간(spring.data.redis.timeout)만큼 기다리게 되므로,
 * 실패가 이어지는 동안에는 바로 예외를 던져 각 기능이 준비한 대체 동작(DB 조회, 서버별 요청 제한 등)으로 넘어가게 한다.
 * 열려 있는 동안 던지는 예외도 DataAccessException이라 기존 Redis 장애 처리 코드가 그대로 받는다.
 */
@Component
public class RedisCircuitBreaker {

	private static final Logger log = LoggerFactory.getLogger(RedisCircuitBreaker.class);
	static final int FAILURE_THRESHOLD = 3;
	static final Duration OPEN_DURATION = Duration.ofSeconds(10);

	private final Clock clock;
	private final AtomicInteger consecutiveFailures = new AtomicInteger();
	private volatile long openUntilMillis;

	@Autowired
	public RedisCircuitBreaker() {
		this(Clock.systemUTC());
	}

	public RedisCircuitBreaker(Clock clock) {
		this.clock = clock;
	}

	public <T> T execute(Supplier<T> redisCall) {
		if (isOpen()) {
			throw new RedisUnavailableException();
		}
		try {
			T result = redisCall.get();
			recordSuccess();
			return result;
		} catch (DataAccessException ex) {
			recordFailure();
			throw ex;
		}
	}

	public void run(Runnable redisCall) {
		execute(() -> {
			redisCall.run();
			return null;
		});
	}

	public boolean isOpen() {
		return clock.millis() < openUntilMillis;
	}

	private void recordSuccess() {
		if (consecutiveFailures.getAndSet(0) >= FAILURE_THRESHOLD) {
			log.info("Redis responded again; resuming Redis calls");
		}
	}

	private void recordFailure() {
		int failures = consecutiveFailures.incrementAndGet();
		if (failures >= FAILURE_THRESHOLD) {
			openUntilMillis = clock.millis() + OPEN_DURATION.toMillis();
			if (failures == FAILURE_THRESHOLD) {
				log.warn("Redis failed {} times in a row; skipping Redis for {} seconds at a time until it recovers",
						failures, OPEN_DURATION.toSeconds());
			}
		}
	}

	/** Redis를 잠시 부르지 않는 동안 던진다. */
	public static class RedisUnavailableException extends DataAccessResourceFailureException {

		RedisUnavailableException() {
			super("Redis calls are paused after repeated failures");
		}
	}
}
