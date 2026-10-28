package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.example.shopping.domain.product.service.ProductReadCache;
import com.example.shopping.global.redis.RedisCircuitBreaker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ProductReadCacheTests {

	@Test
	void 같은_검색_키는_공유_캐시에서_읽고_만료시간을_지정한다() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		Map<String, String> store = new HashMap<>();
		String[] version = { "4" };
		when(values.get(anyString())).thenAnswer(invocation -> {
			String key = invocation.getArgument(0);
			return key.equals("product-read-cache:version") ? version[0] : store.get(key);
		});
		when(values.increment("product-read-cache:version")).thenAnswer(invocation -> {
			version[0] = Long.toString(Long.parseLong(version[0]) + 1);
			return Long.parseLong(version[0]);
		});
		doAnswer(invocation -> {
			store.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(values).set(anyString(), anyString(), any(Duration.class));

		SimpleMeterRegistry meters = new SimpleMeterRegistry();
		ProductReadCache cache = new ProductReadCache(redis, JsonMapper.builder().build(), 60, new RedisCircuitBreaker(),
				meters);
		AtomicInteger loads = new AtomicInteger();
		TypeReference<List<String>> type = new TypeReference<>() {};
		Object key = new TestKey("brands", 10L);

		assertThat(cache.getOrLoad(key, type, () -> {
			loads.incrementAndGet();
			return List.of("3M", "허니웰");
		})).containsExactly("3M", "허니웰");
		assertThat(cache.getOrLoad(key, type, () -> {
			loads.incrementAndGet();
			return List.of("stale");
		})).containsExactly("3M", "허니웰");
		cache.invalidateAfterCommit();
		assertThat(cache.getOrLoad(key, type, () -> {
			loads.incrementAndGet();
			return List.of("new brand");
		})).containsExactly("new brand");

		assertThat(loads).hasValue(2);
		// 적중률을 지표로 남긴다: 처음·무효화 뒤 두 번은 실패, 그 사이 한 번은 적중
		assertThat(meters.get("product.read.cache").tag("result", "miss").counter().count()).isEqualTo(2);
		assertThat(meters.get("product.read.cache").tag("result", "hit").counter().count()).isEqualTo(1);
	}

	private record TestKey(String operation, Long categoryId) {
	}
}
