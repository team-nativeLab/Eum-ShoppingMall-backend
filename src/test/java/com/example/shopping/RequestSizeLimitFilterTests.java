package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.example.shopping.global.security.RequestSizeLimitFilter;

import jakarta.servlet.FilterChain;

class RequestSizeLimitFilterTests {

	private static final int MAX_BYTES = 10;

	// 실제 서버에서는 GlobalExceptionHandler가 413 응답을 쓴다. 여기서는 상태코드만 기록한다.
	private final HandlerExceptionResolver resolver = (request, response, handler, ex) -> {
		response.setStatus(413);
		return new ModelAndView();
	};
	private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(MAX_BYTES, resolver);

	@Test
	void 한도_이내의_본문은_그대로_읽힌다() throws Exception {
		MockHttpServletRequest request = jsonRequest("0123456789");
		MockHttpServletResponse response = new MockHttpServletResponse();
		StringBuilder body = new StringBuilder();

		filter.doFilter(request, response, readBodyInto(body));

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(body).hasToString("0123456789");
	}

	@Test
	void 크기를_밝힌_요청이_한도를_넘으면_읽지_않고_413으로_거절한다() throws Exception {
		MockHttpServletRequest request = jsonRequest("0123456789A");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(413);
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void 크기를_밝히지_않은_요청은_읽다가_한도를_넘는_순간_중단된다() {
		// chunked 전송처럼 Content-Length가 없는 요청
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		request.setContentType("application/json");
		request.setContent("0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
				readBodyInto(new StringBuilder())))
				.isInstanceOf(IOException.class);
	}

	@Test
	void 파일_업로드는_이_필터가_아니라_multipart_설정이_제한한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/uploads");
		request.setContentType("multipart/form-data; boundary=x");
		request.setContent(new byte[1000]);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	private MockHttpServletRequest jsonRequest(String body) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		request.setContentType("application/json");
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		return request;
	}

	/** 컨트롤러가 본문을 읽는 것을 흉내 낸다. */
	private FilterChain readBodyInto(StringBuilder target) {
		return (request, response) -> target
				.append(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
	}
}
