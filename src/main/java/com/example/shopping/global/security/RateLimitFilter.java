package com.example.shopping.global.security;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 악용되기 쉬운 API를 IP별로 분당 횟수 제한한다.
 * - 로그인 없이 호출하거나 비밀번호를 검사하는 API: 비밀번호 대입, 가입·문의 도배, 해시 연산을 노린 부하 공격을 줄인다.
 * - 주문·결제·취소: 호출할 때마다 PG사 API나 재고 변경이 따라오므로 반복 호출로 PG 호출 한도를 소진시키지 못하게 한다.
 * 횟수는 서버 메모리에 두므로 서버가 여러 대면 서버마다 따로 센다 (앞단 WAF 제한과 함께 쓰는 보조 수단).
 */
public class RateLimitFilter extends OncePerRequestFilter {

	// 보안 설정과 같은 방식으로 경로를 비교한다. *는 경로 한 칸(주문번호 등)을 뜻한다.
	private static final List<Rule> RULES = List.of(
			rule(HttpMethod.POST, "/api/v1/auth/login"),
			rule(HttpMethod.POST, "/api/v1/auth/signup"),
			rule(HttpMethod.POST, "/api/v1/auth/refresh"),
			rule(HttpMethod.POST, "/api/v1/admin/auth/login"),
			rule(HttpMethod.PATCH, "/api/v1/me/password"),
			rule(HttpMethod.POST, "/api/v1/quotes"),
			rule(HttpMethod.POST, "/api/v1/support/inquiries"),
			rule(HttpMethod.POST, "/api/v1/orders"),
			rule(HttpMethod.POST, "/api/v1/orders/*/cancel"),
			rule(HttpMethod.POST, "/api/v1/payments/confirm"));

	private static final long WINDOW_MILLIS = 60_000;
	private static final int MAX_TRACKED_KEYS = 100_000;

	private final int requestsPerMinute;
	private final HandlerExceptionResolver resolver;
	private final Clock clock;

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
	private final AtomicLong lastSweptWindow = new AtomicLong();

	public RateLimitFilter(int requestsPerMinute, HandlerExceptionResolver resolver, Clock clock) {
		this.requestsPerMinute = requestsPerMinute;
		this.resolver = resolver;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Rule rule = RULES.stream().filter(r -> r.matcher.matches(request)).findFirst().orElse(null);
		if (rule == null) {
			chain.doFilter(request, response);
			return;
		}

		long currentWindow = clock.millis() / WINDOW_MILLIS;
		sweepOldWindows(currentWindow);

		// 주문번호가 달라도 같은 API면 한 묶음으로 센다.
		String key = request.getRemoteAddr() + " " + rule.name;
		Window window = windows.compute(key, (k, existing) -> existing == null || existing.id != currentWindow
				? new Window(currentWindow, 1)
				: new Window(currentWindow, existing.count + 1));
		if (window.count > requestsPerMinute) {
			resolver.resolveException(request, response, null, new BusinessException(ErrorCode.TOO_MANY_REQUESTS));
			return;
		}
		chain.doFilter(request, response);
	}

	/** 분이 바뀔 때 한 번, 지난 기록을 지운다. 그래도 너무 많으면(대량의 IP로 공격) 전부 비워 메모리를 지킨다. */
	private void sweepOldWindows(long currentWindow) {
		long last = lastSweptWindow.get();
		if (last == currentWindow || !lastSweptWindow.compareAndSet(last, currentWindow)) {
			return;
		}
		windows.values().removeIf(window -> window.id != currentWindow);
		if (windows.size() > MAX_TRACKED_KEYS) {
			windows.clear();
		}
	}

	private static Rule rule(HttpMethod method, String pattern) {
		return new Rule(method + " " + pattern, PathPatternRequestMatcher.withDefaults().matcher(method, pattern));
	}

	private record Rule(String name, RequestMatcher matcher) {
	}

	private record Window(long id, int count) {
	}
}
