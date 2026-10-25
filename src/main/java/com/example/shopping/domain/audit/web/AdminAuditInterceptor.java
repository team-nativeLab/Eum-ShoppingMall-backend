package com.example.shopping.domain.audit.web;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.shopping.domain.audit.service.AdminAuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAuditInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(AdminAuditInterceptor.class);
	private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
	private final AdminAuditLogService auditLogService;

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception exception) {
		String path = request.getRequestURI();
		String method = request.getMethod();
		if (!isAuditedPath(path) || !WRITE_METHODS.contains(method) || response.getStatus() < 200
				|| response.getStatus() >= 300) {
			return;
		}
		Long actorId = authenticatedMemberId();
		if (actorId == null) {
			return;
		}
		String[] segments = path.split("/");
		String targetType = segments.length > 4 ? segments[4] : "admin";
		String targetId = segments.length > 5 && segments[5].length() <= 100 ? segments[5] : null;
		try {
			auditLogService.record(actorId, request.getRemoteAddr(), method, targetType, targetId,
					method + " " + path, response.getStatus());
		} catch (RuntimeException e) {
			log.error("관리자 변경 감사 로그 저장 실패: actor={}, method={}, path={}", actorId, method, path, e);
		}
	}

	private boolean isAuditedPath(String path) {
		// 이미지 업로드는 서버를 거치는 업로드와 직접 업로드의 완료 요청을 남긴다.
		return path.startsWith("/api/v1/admin/") || "/api/v1/uploads".equals(path)
				|| "/api/v1/uploads/complete".equals(path);
	}

	private Long authenticatedMemberId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			return null;
		}
		try {
			return Long.valueOf(jwt.getSubject());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
