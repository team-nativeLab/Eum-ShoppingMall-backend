package com.example.shopping.global.security;

import java.io.IOException;
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

/** Prevents cross-site browser requests from rotating or clearing the refresh-token cookie. */
public class RefreshCookieOriginFilter extends OncePerRequestFilter {

	private static final RequestMatcher COOKIE_AUTH_ENDPOINTS = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/logout"));

	private final List<String> allowedOrigins;
	private final HandlerExceptionResolver resolver;

	public RefreshCookieOriginFilter(List<String> allowedOrigins, HandlerExceptionResolver resolver) {
		this.allowedOrigins = List.copyOf(allowedOrigins);
		this.resolver = resolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String origin = request.getHeader("Origin");
		if (COOKIE_AUTH_ENDPOINTS.matches(request) && origin != null && !allowedOrigins.contains(origin)) {
			resolver.resolveException(request, response, null, new BusinessException(ErrorCode.FORBIDDEN));
			return;
		}
		chain.doFilter(request, response);
	}
}
