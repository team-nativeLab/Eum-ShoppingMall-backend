package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

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
	private final RateLimitFilter filter = new RateLimitFilter(LIMIT, resolver, clock);

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
	void 제한_대상이_아닌_API는_세지_않는다() throws Exception {
		for (int i = 0; i < LIMIT * 5; i++) {
			assertThat(call("GET", "/api/v1/products", "10.0.0.1")).isEqualTo(200);
		}
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

	/** @return 필터가 요청을 통과시켰으면 200, 막았으면 429 */
	private int call(String method, String uri, String ip) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		request.setRemoteAddr(ip);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		boolean passed = chain.getRequest() != null;
		assertThat(passed).isEqualTo(response.getStatus() == 200);
		return response.getStatus();
	}
}
