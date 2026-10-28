package com.example.shopping.global.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.redis.RedisCircuitBreaker;
import com.example.shopping.global.redis.SharedCounter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 악용되기 쉬운 API를 IP별로 분당 횟수 제한한다.
 * - 로그인 없이 호출하거나 비밀번호를 검사하는 API: 비밀번호 대입, 가입·문의 도배, 해시 연산을 노린 부하 공격을 줄인다.
 * - 주문·결제·취소: 호출할 때마다 PG사 API나 재고 변경이 따라오므로 반복 호출로 PG 호출 한도를 소진시키지 못하게 한다.
 * 정상 동작 시 Redis 카운터를 공유하고, Redis 장애 시에는 각 인스턴스의 제한으로 요청을 완화한다(SharedCounter).
 */
public class RateLimitFilter extends OncePerRequestFilter {
	// 어떤 API를 어떻게 제한할지는 ApiPolicy.RATE_LIMITS에 둔다. 보안 설정과 같은 방식으로 경로를 비교한다.
	private static final List<Rule> RULES = ApiPolicy.RATE_LIMITS.stream()
			.map(limit -> new Rule(limit.bucket(),
					PathPatternRequestMatcher.withDefaults().matcher(limit.method(), limit.pattern()), limit.limit()))
			.toList();

	private static final long WINDOW_MILLIS = 60_000;
	private static final int MAX_TRACKED_KEYS = 100_000;

	private final int requestsPerMinute;
	private final int publicProductRequestsPerMinute;
	private final HandlerExceptionResolver resolver;
	private final Clock clock;
	private final SharedCounter counter;

	public RateLimitFilter(int requestsPerMinute, HandlerExceptionResolver resolver, Clock clock,
			StringRedisTemplate redisTemplate) {
		this(requestsPerMinute, 120, resolver, clock, redisTemplate);
	}

	public RateLimitFilter(int requestsPerMinute, int publicProductRequestsPerMinute,
			HandlerExceptionResolver resolver, Clock clock, StringRedisTemplate redisTemplate) {
		this(requestsPerMinute, publicProductRequestsPerMinute, resolver, clock, redisTemplate,
				new RedisCircuitBreaker(clock));
	}

	public RateLimitFilter(int requestsPerMinute, int publicProductRequestsPerMinute,
			HandlerExceptionResolver resolver, Clock clock, StringRedisTemplate redisTemplate,
			RedisCircuitBreaker redisCircuitBreaker) {
		this.requestsPerMinute = requestsPerMinute;
		this.publicProductRequestsPerMinute = publicProductRequestsPerMinute;
		this.resolver = resolver;
		this.clock = clock;
		this.counter = new SharedCounter("rate-limit", redisTemplate, redisCircuitBreaker, clock, MAX_TRACKED_KEYS);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Rule rule = RULES.stream().filter(r -> r.matcher.matches(request)).findFirst().orElse(null);
		if (rule == null) {
			chain.doFilter(request, response);
			return;
		}

		long now = clock.millis();
		// 분 단위 구간마다 센다. 주문번호가 달라도 같은 API면 한 묶음이다.
		String key = "rate-limit:" + now / WINDOW_MILLIS + ":" + request.getRemoteAddr() + " " + rule.bucket;
		long count = counter.increment(key, Duration.ofMillis(WINDOW_MILLIS));
		int limit = switch (rule.limit) {
			case DEFAULT -> requestsPerMinute;
			case PUBLIC_READ -> publicProductRequestsPerMinute;
			// 자동 완성은 검색어 한 번에 여러 번 부르므로 공개 조회 한도의 두 배까지 받는다.
			case AUTOCOMPLETE -> publicProductRequestsPerMinute * 2;
		};
		if (count > limit) {
			long retryAfterSeconds = Math.max(1, (WINDOW_MILLIS - now % WINDOW_MILLIS + 999) / 1000);
			response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
			resolver.resolveException(request, response, null, new BusinessException(ErrorCode.TOO_MANY_REQUESTS));
			return;
		}
		chain.doFilter(request, response);
	}

	private record Rule(String bucket, RequestMatcher matcher, ApiPolicy.Limit limit) {
	}
}
