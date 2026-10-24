package com.example.shopping.domain.audit.web;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import com.example.shopping.domain.audit.service.AdminAuditLogService;
import com.example.shopping.domain.audit.service.AuditDetails;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 관리자 변경 요청이 성공하면 누가, 어떤 대상을, 무엇으로 바꿨는지(요청 본문, 민감한 값 제외) 남긴다.
 * 기록은 변경이 커밋된 뒤 따로 저장한다. 저장에 실패하면 내용 전체를 오류 로그로 남기고
 * admin.audit.record.failures 지표를 올려 알림으로 잡을 수 있게 한다.
 */
@Component
public class AdminAuditInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(AdminAuditInterceptor.class);
	private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
	// 관리자 로그인은 AuthService가 성공·실패를 따로 남긴다. 본문에 비밀번호가 있으므로 여기서는 다루지 않는다.
	private static final String ADMIN_AUTH_PATH = "/api/v1/admin/auth/";

	private final AdminAuditLogService auditLogService;
	private final Counter recordFailures;

	public AdminAuditInterceptor(AdminAuditLogService auditLogService, MeterRegistry meterRegistry) {
		this.auditLogService = auditLogService;
		this.recordFailures = Counter.builder("admin.audit.record.failures")
				.description("Admin audit log entries that could not be saved")
				.register(meterRegistry);
	}

	/** 감사 대상 요청인지: 관리자 API(로그인 제외)와 이미지 업로드의 변경 요청 */
	static boolean isAudited(HttpServletRequest request) {
		String path = request.getRequestURI();
		return WRITE_METHODS.contains(request.getMethod()) && !path.startsWith(ADMIN_AUTH_PATH)
				&& (path.startsWith("/api/v1/admin/") || "/api/v1/uploads".equals(path));
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception exception) {
		String path = request.getRequestURI();
		String method = request.getMethod();
		if (!isAudited(request) || response.getStatus() < 200 || response.getStatus() >= 300) {
			return;
		}
		Long actorId = authenticatedMemberId();
		if (actorId == null) {
			return;
		}
		String[] segments = path.split("/");
		String targetType = segments.length > 4 ? segments[4] : "admin";
		String targetId = segments.length > 5 && segments[5].length() <= 100 ? segments[5] : null;
		String details = details(request);
		try {
			auditLogService.record(actorId, request.getRemoteAddr(), method, targetType, targetId,
					method + " " + path, details, response.getStatus());
		} catch (RuntimeException e) {
			recordFailures.increment();
			log.error("관리자 변경 감사 로그 저장 실패: actor={}, ip={}, method={}, path={}, status={}, details={}", actorId,
					request.getRemoteAddr(), method, path, response.getStatus(), details, e);
		}
	}

	private String details(HttpServletRequest request) {
		ContentCachingRequestWrapper cached = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
		return cached == null ? null : AuditDetails.fromJsonBody(cached.getContentAsByteArray());
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
