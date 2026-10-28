package com.example.shopping.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.redis.RedisCircuitBreaker;

class LoginAttemptLimiterTests {

	private static final int MAX_FAILURES = 3;

	private final AtomicLong now = new AtomicLong(Instant.parse("2026-10-28T00:00:00Z").toEpochMilli());
	private final Clock clock = new Clock() {
		@Override
		public Instant instant() {
			return Instant.ofEpochMilli(now.get());
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	};
	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

	@Test
	void Redis가_있으면_서버끼리_공유한_횟수로_막고_성공하면_지운다() {
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.get(anyString())).thenReturn(String.valueOf(MAX_FAILURES));
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(redis, new RedisCircuitBreaker(clock), MAX_FAILURES, clock);

		assertLocked(limiter, "member@example.com");
		limiter.reset("member@example.com");
		// 이메일을 그대로 키에 남기지 않는다.
		verify(redis).delete(startsWith("auth:login-failures:"));
	}

	@Test
	void Redis가_끊기면_서버별로_세고_10분이_지나면_다시_받는다() {
		when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
		when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<String>anyList(),
				ArgumentMatchers.<Object[]>any())).thenThrow(new RedisConnectionFailureException("down"));
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(redis, new RedisCircuitBreaker(clock), MAX_FAILURES, clock);

		for (int i = 0; i < MAX_FAILURES; i++) {
			limiter.checkAllowed("member@example.com");
			limiter.recordFailure("member@example.com");
		}
		assertLocked(limiter, "member@example.com");
		assertThatCode(() -> limiter.checkAllowed("other@example.com")).doesNotThrowAnyException();

		now.addAndGet(LoginAttemptLimiter.WINDOW.toMillis());
		assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();

		for (int i = 0; i < MAX_FAILURES; i++) {
			limiter.recordFailure("member@example.com");
		}
		limiter.reset("member@example.com");
		assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();
	}

	private static void assertLocked(LoginAttemptLimiter limiter, String email) {
		assertThatThrownBy(() -> limiter.checkAllowed(email))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LOGIN_LOCKED));
	}
}
