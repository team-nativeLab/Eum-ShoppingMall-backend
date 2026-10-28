package com.example.shopping.domain.member.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.redis.RedisCircuitBreaker;

/**
 * 이메일별 로그인 실패 횟수를 센다. IP별 요청 제한(RateLimitFilter)만으로는 IP를 바꿔 가며 한 계정에 비밀번호를 넣어 보는 공격을 막지 못한다.
 * 첫 실패부터 10분 안에 정해진 횟수(기본 10번)를 틀리면 그 10분이 끝날 때까지 맞는 비밀번호로도 로그인할 수 없다(429).
 * 없는 이메일도 똑같이 세어 가입 여부가 드러나지 않게 한다. 로그인에 성공하거나 비밀번호를 재설정하면 센 횟수를 지운다.
 * 정상 동작 시 Redis 카운터를 공유하고, Redis 장애 시에는 서버별로 센다.
 */
@Component
public class LoginAttemptLimiter {

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);
	static final Duration WINDOW = Duration.ofMinutes(10);
	private static final String KEY_PREFIX = "auth:login-failures:";
	// Redis 장애 중 서버별로 기억하는 이메일 수. 넘으면 지난 기록을 지우고, 그래도 많으면 비워 메모리를 지킨다.
	private static final int MAX_LOCAL_EMAILS = 10_000;
	private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
			return count
			""", Long.class);

	private final StringRedisTemplate redis;
	private final RedisCircuitBreaker redisCircuitBreaker;
	private final int maxFailures;
	private final Clock clock;
	private final ConcurrentHashMap<String, Failures> localFailures = new ConcurrentHashMap<>();
	private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean();

	@Autowired
	public LoginAttemptLimiter(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker,
			@Value("${security.login-lock.max-failures:10}") int maxFailures) {
		this(redis, redisCircuitBreaker, maxFailures, Clock.systemUTC());
	}

	LoginAttemptLimiter(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker, int maxFailures,
			Clock clock) {
		this.redis = redis;
		this.redisCircuitBreaker = redisCircuitBreaker;
		this.maxFailures = maxFailures;
		this.clock = clock;
	}

	/** 막힌 이메일이면 LOGIN_LOCKED(429) */
	public void checkAllowed(String email) {
		if (failures(key(email)) >= maxFailures) {
			throw new BusinessException(ErrorCode.LOGIN_LOCKED);
		}
	}

	public void recordFailure(String email) {
		String key = key(email);
		try {
			redisCircuitBreaker.execute(() -> redis.execute(INCREMENT_SCRIPT, List.of(key),
					Long.toString(WINDOW.toMillis())));
			fallbackWarningLogged.set(false);
		} catch (DataAccessException e) {
			warnFallback();
			incrementLocal(key);
		}
	}

	public void reset(String email) {
		String key = key(email);
		localFailures.remove(key);
		try {
			redisCircuitBreaker.execute(() -> redis.delete(key));
		} catch (DataAccessException e) {
			warnFallback();
		}
	}

	private long failures(String key) {
		try {
			String count = redisCircuitBreaker.execute(() -> redis.opsForValue().get(key));
			fallbackWarningLogged.set(false);
			return count == null ? 0 : Long.parseLong(count);
		} catch (DataAccessException e) {
			warnFallback();
			Failures local = localFailures.get(key);
			return local == null || local.expiresAt() <= clock.millis() ? 0 : local.count();
		}
	}

	private void incrementLocal(String key) {
		long now = clock.millis();
		if (localFailures.size() >= MAX_LOCAL_EMAILS) {
			localFailures.values().removeIf(failures -> failures.expiresAt() <= now);
			if (localFailures.size() >= MAX_LOCAL_EMAILS) {
				localFailures.clear();
			}
		}
		localFailures.compute(key, (k, existing) -> existing == null || existing.expiresAt() <= now
				? new Failures(1, now + WINDOW.toMillis())
				: new Failures(existing.count() + 1, existing.expiresAt()));
	}

	private void warnFallback() {
		if (fallbackWarningLogged.compareAndSet(false, true)) {
			log.warn("Redis login-failure counter unavailable; counting failures per instance");
		}
	}

	/** Redis 키에 이메일을 그대로 남기지 않는다. */
	private static String key(String email) {
		return KEY_PREFIX + VerificationCodes.hash("login", email);
	}

	private record Failures(int count, long expiresAt) {
	}
}
