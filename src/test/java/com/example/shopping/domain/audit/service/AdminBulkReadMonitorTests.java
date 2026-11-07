package com.example.shopping.domain.audit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.data.redis.core.script.RedisScript;

import com.example.shopping.global.monitoring.ManualCheckAlerts;
import com.example.shopping.global.redis.RedisCircuitBreaker;

/** 관리자 한 명이 짧은 시간에 개인정보를 많이 조회하면 담당자에게 알린다 (#138). */
class AdminBulkReadMonitorTests {

	private static final String REASON = "admin_bulk_personal_data_read";

	private final AtomicLong now = new AtomicLong(Instant.parse("2026-11-07T00:00:00Z").toEpochMilli());
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
	private final ManualCheckAlerts alerts = mock(ManualCheckAlerts.class);

	@Test
	void 십분_안에_기준_횟수에_닿으면_한_번만_알리고_다른_관리자는_따로_센다() {
		AdminBulkReadMonitor monitor = monitorWithRedisDown(3);

		for (int i = 0; i < 2; i++) {
			monitor.recordRead(7L);
			monitor.recordRead(8L);
		}
		verify(alerts, never()).raise(anyString(), anyString(), any(Object[].class));

		for (int i = 0; i < 5; i++) {
			monitor.recordRead(7L);
		}
		verify(alerts, times(1)).raise(eq(REASON), anyString(), any(Object[].class));

		// 10분이 지나면 처음부터 다시 센다.
		now.addAndGet(AdminBulkReadMonitor.WINDOW.toMillis());
		for (int i = 0; i < 3; i++) {
			monitor.recordRead(7L);
		}
		verify(alerts, times(2)).raise(eq(REASON), anyString(), any(Object[].class));
	}

	private AdminBulkReadMonitor monitorWithRedisDown(int threshold) {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
		when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<String>anyList(),
				ArgumentMatchers.<Object[]>any())).thenThrow(new RedisConnectionFailureException("down"));
		return new AdminBulkReadMonitor(redis, new RedisCircuitBreaker(clock), alerts, threshold, clock);
	}
}
