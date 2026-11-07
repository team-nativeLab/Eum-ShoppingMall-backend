package com.example.shopping.domain.audit.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.shopping.global.monitoring.ManualCheckAlerts;
import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.redis.SharedCounter;

/**
 * 관리자 한 명이 짧은 시간에 개인정보를 많이 조회하면 담당자에게 알린다(#138). 관리자 계정이 탈취돼 회원 정보를
 * 대량으로 빼 가는 상황을 빨리 알아채려는 것이다. 개인정보 조회 API(ApiPolicy.AUDITED_READS)를 부를 때마다 센다.
 * 횟수는 Redis로 서버끼리 공유하고, Redis 장애 중에는 서버별로 센다(SharedCounter).
 */
@Component
public class AdminBulkReadMonitor {

	static final Duration WINDOW = Duration.ofMinutes(10);
	private static final String KEY_PREFIX = "audit:admin-reads:";
	private static final int MAX_LOCAL_KEYS = 1_000;

	private final SharedCounter reads;
	private final ManualCheckAlerts alerts;
	private final int threshold;

	@Autowired
	public AdminBulkReadMonitor(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker,
			ManualCheckAlerts alerts, @Value("${security.audit.bulk-read-alert-threshold:100}") int threshold) {
		this(redis, redisCircuitBreaker, alerts, threshold, Clock.systemUTC());
	}

	AdminBulkReadMonitor(StringRedisTemplate redis, RedisCircuitBreaker redisCircuitBreaker, ManualCheckAlerts alerts,
			int threshold, Clock clock) {
		this.reads = new SharedCounter("admin-read", redis, redisCircuitBreaker, clock, MAX_LOCAL_KEYS);
		this.alerts = alerts;
		this.threshold = threshold;
	}

	/** 개인정보 조회 한 번을 센다. 첫 조회부터 10분 안에 기준 횟수에 닿으면 그때 한 번 알린다. */
	public void recordRead(Long actorMemberId) {
		long count = reads.increment(KEY_PREFIX + actorMemberId, WINDOW);
		if (count == threshold) {
			alerts.raise("admin_bulk_personal_data_read", "관리자 {}가 10분 안에 개인정보 조회 API를 {}번 불렀습니다. 감사 로그를 확인해 주세요.",
					actorMemberId, threshold);
		}
	}
}
