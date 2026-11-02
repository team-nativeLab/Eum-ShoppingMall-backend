package com.example.shopping.global.redis;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 여러 서버가 함께 쓰는 Redis 카운터. 키를 처음 올릴 때 만료 시간을 정하므로, 첫 증가부터 그 시간 동안을 한 구간으로 센다.
 * Redis가 끊기면 이 서버에서 센 값으로 판단한다. 서버별 값은 평소에도 함께 세어, 장애가 나도 0부터 다시 세지 않는다.
 * 서버별 값은 1분마다 만료된 키를 지우고, 그래도 maxLocalKeys를 넘으면(대량의 IP·이메일로 공격) 비워 메모리를 지킨다.
 * IP별 요청 제한(RateLimitFilter)과 이메일별 로그인 잠금(LoginAttemptLimiter)이 함께 쓴다.
 */
public class SharedCounter {

	private static final Logger log = LoggerFactory.getLogger(SharedCounter.class);
	private static final long SWEEP_INTERVAL_MILLIS = 60_000;
	private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
			return count
			""", Long.class);

	private final String name;
	private final StringRedisTemplate redis;
	private final RedisCircuitBreaker redisCircuitBreaker;
	private final Clock clock;
	private final int maxLocalKeys;
	private final ConcurrentHashMap<String, LocalCount> localCounts = new ConcurrentHashMap<>();
	private final AtomicLong lastSweptAt = new AtomicLong();
	private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean();

	/** @param name 로그에 남길 카운터 이름 (예: rate-limit) */
	public SharedCounter(String name, StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker, Clock clock,
			int maxLocalKeys) {
		this.name = name;
		this.redis = redis;
		this.redisCircuitBreaker = redisCircuitBreaker;
		this.clock = clock;
		this.maxLocalKeys = maxLocalKeys;
	}

	/** 1 올리고 올린 값을 돌려준다. 처음 올린 키는 ttl 뒤에 사라진다. */
	public long increment(String key, Duration ttl) {
		long now = clock.millis();
		sweep(now);
		LocalCount local = localCounts.compute(key, (k, existing) -> existing == null || existing.expiresAt() <= now
				? new LocalCount(1, now + ttl.toMillis())
				: new LocalCount(existing.count() + 1, existing.expiresAt()));
		try {
			Long count = redisCircuitBreaker.execute(() -> redis.execute(INCREMENT_SCRIPT, List.of(key),
					Long.toString(ttl.toMillis())));
			if (count == null) {
				throw new DataAccessResourceFailureException("Redis returned no " + name + " counter");
			}
			fallbackWarningLogged.set(false);
			return count;
		} catch (DataAccessException e) {
			warnFallback();
			return local.count();
		}
	}

	/** 지금 값. 없거나 만료됐으면 0 */
	public long get(String key) {
		try {
			String value = redisCircuitBreaker.execute(() -> redis.opsForValue().get(key));
			fallbackWarningLogged.set(false);
			return value == null ? 0 : Long.parseLong(value);
		} catch (DataAccessException e) {
			warnFallback();
			LocalCount local = localCounts.get(key);
			return local == null || local.expiresAt() <= clock.millis() ? 0 : local.count();
		}
	}

	public void reset(String key) {
		localCounts.remove(key);
		try {
			redisCircuitBreaker.execute(() -> redis.delete(key));
		} catch (DataAccessException e) {
			warnFallback();
		}
	}

	private void sweep(long now) {
		long last = lastSweptAt.get();
		if ((now - last < SWEEP_INTERVAL_MILLIS && localCounts.size() < maxLocalKeys)
				|| !lastSweptAt.compareAndSet(last, now)) {
			return;
		}
		localCounts.values().removeIf(count -> count.expiresAt() <= now);
		if (localCounts.size() >= maxLocalKeys) {
			localCounts.clear();
		}
	}

	private void warnFallback() {
		if (fallbackWarningLogged.compareAndSet(false, true)) {
			log.warn("Redis {} counter unavailable; counting per instance until Redis responds", name);
		}
	}

	private record LocalCount(long count, long expiresAt) {
	}
}
