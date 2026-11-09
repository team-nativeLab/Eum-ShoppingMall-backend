package com.example.shopping.global.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
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
 * 다른 사이트의 브라우저 요청이 Refresh 쿠키로 토큰을 재발급받거나 쿠키를 지우지 못하게 한다.
 * 관리자 쿠키는 관리자 화면 주소에서 온 요청만 받는다. 그래서 고객 사이트에 스크립트 삽입(XSS) 같은 문제가 생겨도
 * 같은 브라우저의 관리자 세션으로 관리자 토큰을 받을 수 없다(#163).
 * Origin 헤더가 없는 요청은 브라우저가 보낸 다른 사이트 요청이 아니므로 그대로 보낸다.
 */
public class RefreshCookieOriginFilter extends OncePerRequestFilter {

	private static final RequestMatcher MEMBER_COOKIE_ENDPOINTS = post("/api/v1/auth/refresh", "/api/v1/auth/logout");
	private static final RequestMatcher ADMIN_COOKIE_ENDPOINTS = post(ApiPolicy.ADMIN_REFRESH, ApiPolicy.ADMIN_LOGOUT);

	private final List<String> memberOrigins;
	private final List<String> adminOrigins;
	private final HandlerExceptionResolver resolver;

	/**
	 * @param memberOrigins 회원 쿠키를 받을 Origin(CORS_ALLOWED_ORIGINS)
	 * @param adminOrigins  관리자 쿠키를 받을 Origin(CORS_ADMIN_ORIGINS). 비어 있으면 브라우저 요청은 모두 거절한다.
	 */
	public RefreshCookieOriginFilter(List<String> memberOrigins, List<String> adminOrigins,
			HandlerExceptionResolver resolver) {
		this.memberOrigins = List.copyOf(memberOrigins);
		this.adminOrigins = List.copyOf(adminOrigins);
		this.resolver = resolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String origin = request.getHeader("Origin");
		if (origin != null && (rejected(request, origin, MEMBER_COOKIE_ENDPOINTS, memberOrigins)
				|| rejected(request, origin, ADMIN_COOKIE_ENDPOINTS, adminOrigins))) {
			resolver.resolveException(request, response, null, new BusinessException(ErrorCode.FORBIDDEN));
			return;
		}
		chain.doFilter(request, response);
	}

	private static boolean rejected(HttpServletRequest request, String origin, RequestMatcher endpoints,
			List<String> allowedOrigins) {
		return endpoints.matches(request) && !allowedOrigins.contains(origin);
	}

	private static RequestMatcher post(String... paths) {
		PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
		return new OrRequestMatcher(Arrays.stream(paths)
				.<RequestMatcher>map(path -> builder.matcher(HttpMethod.POST, path))
				.toList());
	}
}
