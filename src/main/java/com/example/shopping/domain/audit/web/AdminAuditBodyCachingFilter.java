package com.example.shopping.domain.audit.web;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 감사 대상 요청(관리자 변경)의 본문을 컨트롤러가 읽는 동안 함께 담아 둔다.
 * 요청이 끝난 뒤 AdminAuditInterceptor가 이 내용으로 무엇을 바꿨는지 남긴다.
 */
@Component
public class AdminAuditBodyCachingFilter extends OncePerRequestFilter {

	// JSON 요청 본문 상한(security.request.max-body-bytes, 기본 1MB)보다 작게 둔다. 기록은 어차피 앞부분만 남긴다.
	private static final int CACHE_LIMIT = 64 * 1024;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !AdminAuditInterceptor.isAudited(request) || !isJson(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		chain.doFilter(new ContentCachingRequestWrapper(request, CACHE_LIMIT), response);
	}

	private boolean isJson(HttpServletRequest request) {
		String contentType = request.getContentType();
		return contentType != null && contentType.toLowerCase().startsWith("application/json");
	}
}
