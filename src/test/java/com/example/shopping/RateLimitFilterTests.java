package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.shopping.global.security.RateLimitFilter;

class RateLimitFilterTests {

	private static final int LIMIT = 3;

	private final AtomicLong now = new AtomicLong(Instant.parse("2026-10-13T00:00:00Z").toEpochMilli());
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
	// 실제 서버에서는 GlobalExceptionHandler가 429 응답을 쓴다. 여기서는 상태코드만 기록한다.
	private final HandlerExceptionResolver resolver = (request, response, handler, ex) -> {
		response.setStatus(429);
		return new ModelAndView();
	};
	private final Map<String, Long> sharedCounters = new ConcurrentHashMap<>();
	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	private final RateLimitFilter filter = new RateLimitFilter(LIMIT, resolver, clock, redisTemplate);

	{
		when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<String>anyList(),
				ArgumentMatchers.<Object[]>any())).thenAnswer(invocation -> {
			List<String> keys = invocation.getArgument(1);
			return sharedCounters.merge(keys.get(0), 1L, Long::sum);
		});
	}

	@Test
	void 한도를_넘은_요청은_컨트롤러에_도달하지_못하고_429를_받는다() throws Exception {
		for (int i = 0; i < LIMIT; i++) {
			assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1")).isEqualTo(200);
		}
		assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1")).isEqualTo(429);
		assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1")).isEqualTo(429);
	}

	@Test
	void 한도는_IP와_API별로_따로_센다() throws Exception {
		for (int i = 0; i < LIMIT; i++) {
			call("POST", "/api/v1/auth/login", "10.0.0.1");
		}
		assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1")).isEqualTo(429);

		assertThat(call("POST", "/api/v1/auth/login", "10.0.0.2")).isEqualTo(200);
		assertThat(call("POST", "/api/v1/auth/signup", "10.0.0.1")).isEqualTo(200);
	}

	@Test
	void 회원과_관리자_로그인은_같은_IP_한도를_공유한다() throws Exception {
		assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1")).isEqualTo(200);
		assertThat(call("POST", "/api/v1/admin/auth/login", "10.0.0.1")).isEqualTo(200);
		assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1")).isEqualTo(200);
		assertThat(call("POST", "/api/v1/admin/auth/login", "10.0.0.1")).isEqualTo(429);

		// 다른 IP의 사용자는 같은 이메일이라도 독립적으로 시도할 수 있다.
		assertThat(call("POST", "/api/v1/auth/login", "10.0.0.2")).isEqualTo(200);
	}

	@Test
	void 주문_취소처럼_주문번호가_들어가는_API는_번호가_달라도_한_묶음으로_센다() throws Exception {
		assertThat(call("POST", "/api/v1/orders/ORDER-A/cancel", "10.0.0.1")).isEqualTo(200);
		assertThat(call("POST", "/api/v1/orders/ORDER-B/cancel", "10.0.0.1")).isEqualTo(200);
		assertThat(call("POST", "/api/v1/orders/ORDER-C/cancel", "10.0.0.1")).isEqualTo(200);
		assertThat(call("POST", "/api/v1/orders/ORDER-D/cancel", "10.0.0.1")).isEqualTo(429);

		// 결제 승인·주문 생성도 제한 대상이고, 주문 조회는 아니다.
		for (int i = 0; i < LIMIT; i++) {
			call("POST", "/api/v1/payments/confirm", "10.0.0.1");
			call("POST", "/api/v1/orders", "10.0.0.1");
		}
		assertThat(call("POST", "/api/v1/payments/confirm", "10.0.0.1")).isEqualTo(429);
		assertThat(call("POST", "/api/v1/orders", "10.0.0.1")).isEqualTo(429);
		assertThat(call("GET", "/api/v1/orders", "10.0.0.1")).isEqualTo(200);
		assertThat(call("GET", "/api/v1/orders/ORDER-A", "10.0.0.1")).isEqualTo(200);
	}

	@Test
	void 공개_공지_조회는_요청_제한을_세지_않는다() throws Exception {
		for (int i = 0; i < LIMIT * 5; i++) {
			assertThat(call("GET", "/api/v1/notices", "10.0.0.1")).isEqualTo(200);
		}
	}

	@Test
	void 공개_상품_조회는_별도_설정한_IP별_한도를_공유한다() throws Exception {
		RateLimitFilter searchFilter = new RateLimitFilter(10, 5, resolver, clock, redisTemplate);
		for (int i = 0; i < 5; i++) {
			assertThat(call(searchFilter, "GET", "/api/v1/products/suggestions", "10.0.0.12"))
					.isEqualTo(200);
		}
		assertThat(call(searchFilter, "GET", "/api/v1/products/facets", "10.0.0.12")).isEqualTo(429);
		assertThat(call(searchFilter, "GET", "/api/v1/products", "10.0.0.13")).isEqualTo(200);
	}

	@Test
	void 일_분이_지나면_다시_요청할_수_있다() throws Exception {
		for (int i = 0; i <= LIMIT; i++) {
			call("POST", "/api/v1/quotes", "10.0.0.1");
		}
		assertThat(call("POST", "/api/v1/quotes", "10.0.0.1")).isEqualTo(429);

		now.addAndGet(60_000);
		assertThat(call("POST", "/api/v1/quotes", "10.0.0.1")).isEqualTo(200);
	}

	@Test
	void 서로_다른_필터_인스턴스도_Redis_카운터를_공유한다() throws Exception {
		RateLimitFilter secondInstance = new RateLimitFilter(LIMIT, resolver, clock, redisTemplate);
		for (int i = 0; i < LIMIT; i++) {
			assertThat(call("POST", "/api/v1/auth/login", "10.0.0.25")).isEqualTo(200);
		}
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		request.setRemoteAddr("10.0.0.25");
		MockHttpServletResponse response = new MockHttpServletResponse();
		secondInstance.doFilter(request, response, new MockFilterChain());
		assertThat(response.getStatus()).isEqualTo(429);
	}

	@Test
	void 한도_초과_응답에_다음_분까지의_Retry_After를_포함한다() throws Exception {
		for (int i = 0; i <= LIMIT; i++) {
			call("POST", "/api/v1/auth/login", "10.0.0.30");
		}
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		request.setRemoteAddr("10.0.0.30");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		assertThat(response.getStatus()).isEqualTo(429);
		assertThat(response.getHeader("Retry-After")).isEqualTo("60");
	}

	@Test
	void Redis_장애_시에는_인스턴스별_제한으로_전환한다() throws Exception {
		StringRedisTemplate unavailableRedis = mock(StringRedisTemplate.class);
		when(unavailableRedis.execute(ArgumentMatchers.<RedisScript<Long>>any(), ArgumentMatchers.<String>anyList(),
				ArgumentMatchers.<Object[]>any())).thenThrow(new DataAccessResourceFailureException("redis unavailable"));
		RateLimitFilter fallbackFilter = new RateLimitFilter(LIMIT, resolver, clock, unavailableRedis);
		for (int i = 0; i < LIMIT; i++) {
			assertThat(call(fallbackFilter, "10.0.0.40")).isEqualTo(200);
		}
		assertThat(call(fallbackFilter, "10.0.0.40")).isEqualTo(429);
	}

	@Test
	void Redis가_장애나기_전에_사용한_한도는_로컬_대체_제한에도_반영한다() throws Exception {
		StringRedisTemplate failingAfterTwoRequests = mock(StringRedisTemplate.class);
		when(failingAfterTwoRequests.execute(ArgumentMatchers.<RedisScript<Long>>any(),
				ArgumentMatchers.<String>anyList(), ArgumentMatchers.<Object[]>any()))
				.thenReturn(1L, 2L).thenThrow(new DataAccessResourceFailureException("redis unavailable"));
		RateLimitFilter fallbackFilter = new RateLimitFilter(LIMIT, resolver, clock, failingAfterTwoRequests);
		assertThat(call(fallbackFilter, "10.0.0.41")).isEqualTo(200);
		assertThat(call(fallbackFilter, "10.0.0.41")).isEqualTo(200);
		assertThat(call(fallbackFilter, "10.0.0.41")).isEqualTo(200);
		assertThat(call(fallbackFilter, "10.0.0.41")).isEqualTo(429);
	}

	/** @return 필터가 요청을 통과시켰으면 200, 막았으면 429 */
	private int call(String method, String uri, String ip) throws Exception {
		return call(filter, method, uri, ip);
	}

	private int call(RateLimitFilter target, String ip) throws Exception {
		return call(target, "POST", "/api/v1/auth/login", ip);
	}

	private int call(RateLimitFilter target, String method, String uri, String ip) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		request.setRemoteAddr(ip);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		target.doFilter(request, response, chain);

		boolean passed = chain.getRequest() != null;
		assertThat(passed).isEqualTo(response.getStatus() == 200);
		return response.getStatus();
	}
}
