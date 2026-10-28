package com.example.shopping.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class SharedCounterTest {

	private static final Duration TTL = Duration.ofMinutes(1);

	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void Redis가_끊기면_서버별로_세고_기억하는_키가_너무_많으면_비워_메모리를_지킨다() {
		when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
		when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<String>anyList(),
				ArgumentMatchers.<Object[]>any())).thenThrow(new RedisConnectionFailureException("down"));
		SharedCounter counter = new SharedCounter("test", redis, new RedisCircuitBreaker(clock), clock, 2);

		assertThat(counter.increment("a", TTL)).isEqualTo(1);
		assertThat(counter.increment("a", TTL)).isEqualTo(2);
		assertThat(counter.increment("b", TTL)).isEqualTo(1);
		assertThat(counter.get("a")).isEqualTo(2);

		// 상한(2개)에 닿으면 만료되지 않은 키까지 비운다.
		assertThat(counter.increment("c", TTL)).isEqualTo(1);
		assertThat(counter.get("a")).isZero();

		counter.reset("c");
		assertThat(counter.get("c")).isZero();
	}
}
