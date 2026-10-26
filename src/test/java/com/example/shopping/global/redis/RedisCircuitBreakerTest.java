package com.example.shopping.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.example.shopping.global.redis.RedisCircuitBreaker.RedisUnavailableException;

class RedisCircuitBreakerTest {

	private final MutableClock clock = new MutableClock();
	private final RedisCircuitBreaker breaker = new RedisCircuitBreaker(clock);
	private final AtomicInteger calls = new AtomicInteger();

	@Test
	void 연달아_실패하면_잠시_Redis를_부르지_않고_바로_예외를_던진다() {
		for (int i = 0; i < RedisCircuitBreaker.FAILURE_THRESHOLD; i++) {
			assertThatThrownBy(() -> breaker.execute(this::failingCall))
					.isInstanceOf(RedisConnectionFailureException.class);
		}

		assertThat(breaker.isOpen()).isTrue();
		assertThatThrownBy(() -> breaker.execute(this::failingCall)).isInstanceOf(RedisUnavailableException.class);
		assertThat(calls).hasValue(RedisCircuitBreaker.FAILURE_THRESHOLD);
	}

	@Test
	void 기다린_뒤_다시_시도해_성공하면_정상으로_돌아오고_실패하면_다시_멈춘다() {
		for (int i = 0; i < RedisCircuitBreaker.FAILURE_THRESHOLD; i++) {
			assertThatThrownBy(() -> breaker.execute(this::failingCall));
		}
		clock.advance(RedisCircuitBreaker.OPEN_DURATION.toMillis());

		assertThat(breaker.isOpen()).isFalse();
		assertThatThrownBy(() -> breaker.execute(this::failingCall))
				.isInstanceOf(RedisConnectionFailureException.class);
		assertThat(breaker.isOpen()).isTrue();

		clock.advance(RedisCircuitBreaker.OPEN_DURATION.toMillis());
		assertThat(breaker.execute(() -> "ok")).isEqualTo("ok");
		assertThat(breaker.isOpen()).isFalse();
		// 성공하면 실패 횟수를 새로 센다.
		assertThatThrownBy(() -> breaker.execute(this::failingCall));
		assertThat(breaker.isOpen()).isFalse();
	}

	@Test
	void 한두_번_실패는_계속_Redis를_부른다() {
		assertThatThrownBy(() -> breaker.execute(this::failingCall));
		assertThat(breaker.execute(() -> "ok")).isEqualTo("ok");
		assertThatThrownBy(() -> breaker.execute(this::failingCall));
		assertThatThrownBy(() -> breaker.execute(this::failingCall));

		assertThat(breaker.isOpen()).isFalse();
	}

	private String failingCall() {
		calls.incrementAndGet();
		throw new RedisConnectionFailureException("down");
	}

	private static class MutableClock extends Clock {

		private long millis = 1_000_000;

		void advance(long delta) {
			millis += delta;
		}

		@Override
		public long millis() {
			return millis;
		}

		@Override
		public Instant instant() {
			return Instant.ofEpochMilli(millis);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}
}
