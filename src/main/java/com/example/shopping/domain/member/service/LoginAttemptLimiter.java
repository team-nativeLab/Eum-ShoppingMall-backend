package com.example.shopping.domain.member.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.redis.SharedCounter;

/**
 * 이메일별 로그인 실패 횟수를 센다. IP별 요청 제한(RateLimitFilter)만으로는 IP를 바꿔 가며 한 계정에 비밀번호를 넣어 보는 공격을 막지 못한다.
 * 첫 실패부터 10분 안에 정해진 횟수(기본 10번)를 틀리면 그 10분이 끝날 때까지 맞는 비밀번호로도 로그인할 수 없다(429).
 * 없는 이메일도 똑같이 세어 가입 여부가 드러나지 않게 한다. 로그인에 성공하거나 비밀번호를 재설정하면 센 횟수를 지운다.
 * 횟수는 Redis로 서버끼리 공유하고, Redis 장애 중에는 서버별로 센다(SharedCounter).
 */
@Component
public class LoginAttemptLimiter {

	static final Duration WINDOW = Duration.ofMinutes(10);
	private static final String KEY_PREFIX = "auth:login-failures:";
	private static final int MAX_LOCAL_EMAILS = 10_000;

	private final SharedCounter failures;
	private final int maxFailures;

	@Autowired
	public LoginAttemptLimiter(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker,
			@Value("${security.login-lock.max-failures:10}") int maxFailures) {
		this(redis, redisCircuitBreaker, maxFailures, Clock.systemUTC());
	}

	LoginAttemptLimiter(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker, int maxFailures,
			Clock clock) {
		this.failures = new SharedCounter("login-failure", redis, redisCircuitBreaker, clock, MAX_LOCAL_EMAILS);
		this.maxFailures = maxFailures;
	}

	/** 막힌 이메일이면 LOGIN_LOCKED(429) */
	public void checkAllowed(String email) {
		if (failures.get(key(email)) >= maxFailures) {
			throw new BusinessException(ErrorCode.LOGIN_LOCKED);
		}
	}

	public void recordFailure(String email) {
		failures.increment(key(email), WINDOW);
	}

	public void reset(String email) {
		failures.reset(key(email));
	}

	/** Redis 키에 이메일을 그대로 남기지 않는다. */
	private static String key(String email) {
		return KEY_PREFIX + VerificationCodes.hash("login", email);
	}
}
