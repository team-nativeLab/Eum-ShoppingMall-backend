package com.example.shopping.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.example.shopping.domain.member.entity.VerificationPurpose;
import com.example.shopping.global.captcha.CaptchaVerifier;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.redis.RedisCircuitBreaker;

/** 인증번호 실패 횟수: 3번이면 로봇 확인, 5번이면 1시간 차단 (#130). Redis가 끊긴 상태(서버별로 셈)에서 시간을 옮겨 확인한다. */
class VerificationAttemptGuardTests {

	private static final String EMAIL = "member@example.com";
	private static final VerificationPurpose SIGNUP = VerificationPurpose.SIGNUP;

	private final AtomicLong now = new AtomicLong(Instant.parse("2026-11-06T00:00:00Z").toEpochMilli());
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
		public Clock withZone(ZoneId zone) {
			return this;
		}
	};
	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private boolean captchaEnabled = true;

	private final CaptchaVerifier captcha = new CaptchaVerifier() {
		@Override
		public boolean isEnabled() {
			return captchaEnabled;
		}

		@Override
		public boolean verify(String token, String remoteIp) {
			return "human".equals(token);
		}
	};

	private VerificationAttemptGuard guard;

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
		when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<String>anyList(),
				ArgumentMatchers.<Object[]>any())).thenThrow(new RedisConnectionFailureException("down"));
		when(redis.delete(ArgumentMatchers.anyString())).thenThrow(new RedisConnectionFailureException("down"));
		guard = new VerificationAttemptGuard(redis, new RedisCircuitBreaker(clock), captcha, 3, 5, clock);
	}

	@Test
	void 세_번_틀리면_로봇_확인을_통과해야_하고_다른_이메일과_용도는_따로_센다() {
		for (int i = 0; i < 2; i++) {
			assertThat(guard.recordFailure(EMAIL, SIGNUP)).isFalse();
		}
		assertThatCode(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, null, null)).doesNotThrowAnyException();

		guard.recordFailure(EMAIL, SIGNUP);
		assertError(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, null, null), ErrorCode.CAPTCHA_REQUIRED);
		assertError(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, "robot", null), ErrorCode.CAPTCHA_INVALID);
		assertThatCode(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, "human", null)).doesNotThrowAnyException();

		assertThatCode(() -> guard.checkAttemptAllowed("other@example.com", SIGNUP, null, null))
				.doesNotThrowAnyException();
		assertThatCode(() -> guard.checkAttemptAllowed(EMAIL, VerificationPurpose.PASSWORD_RESET, null, null))
				.doesNotThrowAnyException();
	}

	@Test
	void 다섯_번_틀리면_그때부터_1시간_동안_막고_지나면_처음부터_다시_센다() {
		for (int i = 0; i < 4; i++) {
			assertThat(guard.recordFailure(EMAIL, SIGNUP)).isFalse();
		}
		// 첫 실패에서 50분이 지나 다섯 번째로 틀려도 그때부터 1시간을 막는다.
		now.addAndGet(Duration.ofMinutes(50).toMillis());
		assertThat(guard.recordFailure(EMAIL, SIGNUP)).isTrue();
		assertError(() -> guard.checkNotLocked(EMAIL, SIGNUP), ErrorCode.VERIFICATION_LOCKED);
		assertError(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, "human", null), ErrorCode.VERIFICATION_LOCKED);

		now.addAndGet(Duration.ofMinutes(59).toMillis());
		assertError(() -> guard.checkNotLocked(EMAIL, SIGNUP), ErrorCode.VERIFICATION_LOCKED);

		now.addAndGet(Duration.ofMinutes(1).toMillis());
		assertThatCode(() -> guard.checkNotLocked(EMAIL, SIGNUP)).doesNotThrowAnyException();
		// 막히기 전의 실패는 남지 않아 로봇 확인 없이 다시 시작한다.
		assertThatCode(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, null, null)).doesNotThrowAnyException();
	}

	@Test
	void 첫_실패부터_1시간이_지나면_틀린_횟수를_다시_센다() {
		for (int i = 0; i < 3; i++) {
			guard.recordFailure(EMAIL, SIGNUP);
		}
		now.addAndGet(VerificationAttemptGuard.FAILURE_WINDOW.toMillis());

		assertThatCode(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, null, null)).doesNotThrowAnyException();
		for (int i = 0; i < 4; i++) {
			assertThat(guard.recordFailure(EMAIL, SIGNUP)).isFalse();
		}
	}

	@Test
	void 인증에_성공하면_센_횟수를_지운다() {
		for (int i = 0; i < 4; i++) {
			guard.recordFailure(EMAIL, SIGNUP);
		}
		guard.reset(EMAIL, SIGNUP);

		assertThatCode(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, null, null)).doesNotThrowAnyException();
		assertThat(guard.recordFailure(EMAIL, SIGNUP)).isFalse();
	}

	@Test
	void 로봇_확인을_끄면_토큰_없이_확인하고_5번째에_막는다() {
		captchaEnabled = false;
		for (int i = 0; i < 4; i++) {
			guard.recordFailure(EMAIL, SIGNUP);
		}
		assertThatCode(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, null, null)).doesNotThrowAnyException();

		assertThat(guard.recordFailure(EMAIL, SIGNUP)).isTrue();
		assertError(() -> guard.checkAttemptAllowed(EMAIL, SIGNUP, null, null), ErrorCode.VERIFICATION_LOCKED);
	}

	private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}
}
