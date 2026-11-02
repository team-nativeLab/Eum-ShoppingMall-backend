package com.example.shopping.domain.product.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.shopping.global.redis.RedisCircuitBreaker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공유 Redis를 이용하는 짧은 상품 조회 캐시(홈, 카테고리, 목록·검색, 필터 개수, 자동 완성). Redis가 끊기면 캐시를 건너뛰고 원래 조회를 실행한다.
 * 관리자가 상품·카테고리·배너를 바꿀 때만 전체를 무효화한다. 주문·결제·취소로 바뀌는 재고(품절 표시)와 판매 수량(인기순)은
 * 무효화하지 않고 TTL(기본 60초) 안에 반영한다. 자주 바뀌는 값 때문에 전체를 비우면 주문이 몰릴수록 캐시가 맞지 않기 때문이다.
 * 상품 상세는 캐시하지 않고, 장바구니·주문은 재고를 다시 확인한다. 적중률은 product.read.cache 지표(result=hit/miss/unavailable)로 본다.
 */
@Component
public class ProductReadCache {

	private static final Logger log = LoggerFactory.getLogger(ProductReadCache.class);
	private static final String VERSION_KEY = "product-read-cache:version";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;
	private final Duration ttl;
	private final RedisCircuitBreaker redisCircuitBreaker;
	private final Counter hits;
	private final Counter misses;
	private final Counter unavailable;

	public ProductReadCache(StringRedisTemplate redis, JsonMapper jsonMapper, long ttlSeconds) {
		this(redis, jsonMapper, ttlSeconds, new RedisCircuitBreaker(), new SimpleMeterRegistry());
	}

	@org.springframework.beans.factory.annotation.Autowired
	public ProductReadCache(StringRedisTemplate redis, JsonMapper jsonMapper,
			@org.springframework.beans.factory.annotation.Value("${app.cache.product-ttl-seconds:60}") long ttlSeconds,
			RedisCircuitBreaker redisCircuitBreaker, MeterRegistry meterRegistry) {
		this.redis = redis;
		this.jsonMapper = jsonMapper;
		this.ttl = Duration.ofSeconds(Math.max(0, ttlSeconds));
		this.redisCircuitBreaker = redisCircuitBreaker;
		this.hits = counter(meterRegistry, "hit");
		this.misses = counter(meterRegistry, "miss");
		this.unavailable = counter(meterRegistry, "unavailable");
	}

	private static Counter counter(MeterRegistry meterRegistry, String result) {
		return Counter.builder("product.read.cache").tag("result", result)
				.description("Product read cache lookups by result").register(meterRegistry);
	}

	public <T> T getOrLoad(Object key, Class<T> type, Supplier<T> loader) {
		return getOrLoadValue(key, loader, json -> jsonMapper.readValue(json, type));
	}

	public <T> T getOrLoad(Object key, TypeReference<T> type, Supplier<T> loader) {
		return getOrLoadValue(key, loader, json -> jsonMapper.readValue(json, type));
	}

	private <T> T getOrLoadValue(Object key, Supplier<T> loader, JsonReader<T> reader) {
		if (ttl.isZero()) {
			return loader.get();
		}
		String cacheKey;
		try {
			String version = redisCircuitBreaker.execute(() -> redis.opsForValue().get(VERSION_KEY));
			cacheKey = "product-read-cache:" + (version == null ? "0" : version) + ":"
					+ hash(jsonMapper.writeValueAsString(key));
			String cached = redisCircuitBreaker.execute(() -> redis.opsForValue().get(cacheKey));
			if (cached != null) {
				T value = reader.read(cached);
				hits.increment();
				return value;
			}
		} catch (DataAccessException | JacksonException | IllegalArgumentException ex) {
			log.debug("Product read cache unavailable; reading from the database", ex);
			unavailable.increment();
			return loader.get();
		}
		misses.increment();

		T value = loader.get();
		try {
			String json = jsonMapper.writeValueAsString(value);
			redisCircuitBreaker.run(() -> redis.opsForValue().set(cacheKey, json, ttl));
		} catch (DataAccessException | JacksonException | IllegalArgumentException ex) {
			log.debug("Could not write product read cache", ex);
		}
		return value;
	}

	/** 변경 사항이 롤백되면 캐시 버전은 유지하고, 커밋 성공 후에만 다른 인스턴스에도 무효화를 알린다. */
	public void invalidateAfterCommit() {
		if (ttl.isZero()) {
			return;
		}
		Runnable invalidate = () -> {
			try {
				redisCircuitBreaker.run(() -> redis.opsForValue().increment(VERSION_KEY));
			} catch (DataAccessException ex) {
				log.warn("Could not invalidate product read cache; entries expire after {} seconds", ttl.toSeconds(), ex);
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					invalidate.run();
				}
			});
		} else {
			invalidate.run();
		}
	}

	@FunctionalInterface
	private interface JsonReader<T> {
		T read(String json) throws JacksonException;
	}

	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}
}
