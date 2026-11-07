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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.example.shopping.global.captcha.CaptchaVerifier;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.security.KeyedHash;
import com.example.shopping.global.security.SecretHashes;

class LoginAttemptLimiterTests {

	private static final int CAPTCHA_AFTER_FAILURES = 2;
	private static final int MAX_FAILURES = 3;
	private static final String EMAIL = "member@example.com";
	private static final KeyedHash KEY_HASHES = KeyedHash.derive("test-secret-0123456789abcdef0123",
			SecretHashes.LOGIN_LOCK_KEY);

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
	private final List<String> captchaCalls = new CopyOnWriteArrayList<>();
	private boolean captchaEnabled;
	private final CaptchaVerifier captcha = new CaptchaVerifier() {
		@Override
		public boolean isEnabled() {
			return captchaEnabled;
		}

		@Override
		public boolean verify(String token, String action, String remoteIp) {
			captchaCalls.add(action + ":" + token + "@" + remoteIp);
			return "human".equals(token);
		}
	};

	@Test
	void 로봇_확인을_끄면_Redis로_공유한_횟수로_막고_성공하면_지운다() {
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.get(anyString())).thenReturn(String.valueOf(MAX_FAILURES));
		LoginAttemptLimiter limiter = limiter();

		assertRejected(limiter, null, ErrorCode.LOGIN_LOCKED);
		limiter.reset(EMAIL);
		// 이메일을 그대로 키에 남기지 않는다.
		verify(redis).delete(startsWith("auth:login-failures:"));
	}

	@Test
	void 로봇_확인을_끄면_Redis가_끊겨도_서버별로_세고_10분이_지나면_다시_받는다() {
		redisDown();
		LoginAttemptLimiter limiter = limiter();

		for (int i = 0; i < MAX_FAILURES; i++) {
			limiter.checkAllowed(EMAIL, null, null);
			limiter.recordFailure(EMAIL);
		}
		assertRejected(limiter, null, ErrorCode.LOGIN_LOCKED);
		assertThatCode(() -> limiter.checkAllowed("other@example.com", null, null)).doesNotThrowAnyException();

		now.addAndGet(LoginAttemptLimiter.WINDOW.toMillis());
		assertThatCode(() -> limiter.checkAllowed(EMAIL, null, null)).doesNotThrowAnyException();

		for (int i = 0; i < MAX_FAILURES; i++) {
			limiter.recordFailure(EMAIL);
		}
		limiter.reset(EMAIL);
		assertThatCode(() -> limiter.checkAllowed(EMAIL, null, null)).doesNotThrowAnyException();
	}

	@Test
	void 로봇_확인을_켜면_많이_틀린_이메일에_토큰을_요구하고_막지는_않는다() {
		redisDown();
		captchaEnabled = true;
		LoginAttemptLimiter limiter = limiter();
		for (int i = 0; i < CAPTCHA_AFTER_FAILURES; i++) {
			limiter.checkAllowed(EMAIL, null, "203.0.113.1");
			limiter.recordFailure(EMAIL);
		}
		assertThat(captchaCalls).isEmpty();

		assertRejected(limiter, null, ErrorCode.CAPTCHA_REQUIRED);
		assertRejected(limiter, "robot", ErrorCode.CAPTCHA_INVALID);
		// 막는 횟수(3번)를 넘겨도 로봇 확인만 통과하면 주인은 계속 로그인할 수 있다(#141).
		for (int i = 0; i < MAX_FAILURES * 3; i++) {
			limiter.recordFailure(EMAIL);
		}
		assertThatCode(() -> limiter.checkAllowed(EMAIL, "human", "203.0.113.1")).doesNotThrowAnyException();
		assertThat(captchaCalls).contains("login:human@203.0.113.1");
	}

	private LoginAttemptLimiter limiter() {
		return new LoginAttemptLimiter(redis, new RedisCircuitBreaker(clock), KEY_HASHES, captcha,
				CAPTCHA_AFTER_FAILURES, MAX_FAILURES, clock);
	}

	private void redisDown() {
		when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
		when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<String>anyList(),
				ArgumentMatchers.<Object[]>any())).thenThrow(new RedisConnectionFailureException("down"));
	}

	private static void assertRejected(LoginAttemptLimiter limiter, String captchaToken, ErrorCode expected) {
		assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, captchaToken, "203.0.113.1"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}
}
