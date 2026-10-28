package com.example.shopping.global.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpMethod;
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
	// 보안 설정과 같은 방식으로 경로를 비교한다. *는 경로 한 칸(주문번호 등)을 뜻한다.
	private static final List<Rule> RULES = List.of(
			rule(HttpMethod.POST, "/api/v1/auth/login", "POST /api/v1/login"),
			rule(HttpMethod.POST, "/api/v1/auth/signup"),
			rule(HttpMethod.POST, "/api/v1/auth/refresh"),
			// 인증번호 발송(메일 비용·스팸)과 확인(번호 대입)을 IP별로 제한한다.
			rule(HttpMethod.POST, "/api/v1/auth/email-verifications"),
			rule(HttpMethod.POST, "/api/v1/auth/email-verifications/confirm"),
			rule(HttpMethod.POST, "/api/v1/auth/password-reset"),
			rule(HttpMethod.POST, "/api/v1/auth/password-reset/confirm"),
			rule(HttpMethod.POST, "/api/v1/admin/auth/login", "POST /api/v1/login"),
			rule(HttpMethod.PATCH, "/api/v1/me/password"),
			rule(HttpMethod.POST, "/api/v1/quotes"),
			rule(HttpMethod.POST, "/api/v1/support/inquiries"),
			rule(HttpMethod.POST, "/api/v1/orders"),
			rule(HttpMethod.POST, "/api/v1/orders/*/cancel"),
			rule(HttpMethod.POST, "/api/v1/payments/confirm"),
			rule(HttpMethod.POST, "/api/v1/uploads/presigned-url"),
			rule(HttpMethod.POST, "/api/v1/uploads"),
			// 공개 상품 조회는 화면 기능별로 따로 센다. 검색 한 번에 목록·필터 개수를 함께 부르고,
			// 자동 완성은 글자를 칠 때마다 부르므로 한 묶음으로 세면 한 사무실(같은 공인 IP) 직원 몇 명만으로도 막힌다.
			publicRead("/api/v1/products/suggestions", "GET /api/v1/products/suggestions", Limit.AUTOCOMPLETE),
			publicRead("/api/v1/products/facets", "GET /api/v1/products/facets", Limit.PUBLIC_READ),
			publicRead("/api/v1/products", "GET /api/v1/products", Limit.PUBLIC_READ),
			publicRead("/api/v1/products/**", "GET /api/v1/products/{productId}", Limit.PUBLIC_READ));

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

	private static Rule rule(HttpMethod method, String pattern) {
		return rule(method, pattern, method + " " + pattern);
	}

	private static Rule rule(HttpMethod method, String pattern, String bucket) {
		return new Rule(bucket, PathPatternRequestMatcher.withDefaults().matcher(method, pattern), Limit.DEFAULT);
	}

	private static Rule publicRead(String pattern, String bucket, Limit limit) {
		return new Rule(bucket, PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, pattern), limit);
	}

	private enum Limit {
		DEFAULT, PUBLIC_READ, AUTOCOMPLETE
	}

	private record Rule(String bucket, RequestMatcher matcher, Limit limit) {
	}
}
