package com.example.shopping.domain.audit.web;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import com.example.shopping.domain.audit.service.AdminAuditLogService;
import com.example.shopping.domain.audit.service.AuditDetails;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 관리자 변경 요청을 누가, 어떤 대상을, 무엇으로 바꾸려 했는지(요청 본문, 민감한 값 제외) 남긴다.
 * 성공한 요청과 권한이 없어 거절된 요청(403)을 남기고, 입력 오류(400)·없는 대상(404) 같은 실패는 남기지 않는다.
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

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception exception) {
		String path = request.getRequestURI();
		String method = request.getMethod();
		// 컨트롤러까지 온 요청만 남긴다. 없는 경로로 보낸 요청이 기록을 채우지 못하게 한다.
		if (!(handler instanceof HandlerMethod) || !isAudited(request) || !isRecordedStatus(response.getStatus())) {
			return;
		}
		Long actorId = authenticatedMemberId();
		if (actorId == null) {
			return;
		}
		Target target = target(request);
		String details = details(request);
		try {
			auditLogService.record(actorId, request.getRemoteAddr(), method, target.type(), target.id(),
					method + " " + path, details, response.getStatus());
		} catch (RuntimeException e) {
			recordFailures.increment();
			log.error("관리자 변경 감사 로그 저장 실패: actor={}, ip={}, method={}, path={}, status={}, details={}", actorId,
					request.getRemoteAddr(), method, path, response.getStatus(), details, e);
		}
	}

	private static boolean isRecordedStatus(int status) {
		return (status >= 200 && status < 300) || status == HttpServletResponse.SC_FORBIDDEN;
	}

	@SuppressWarnings("unchecked")
	private static Target target(HttpServletRequest request) {
		String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		Map<String, String> variables = (Map<String, String>) request
				.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		return target(pattern == null ? request.getRequestURI() : pattern, variables == null ? Map.of() : variables);
	}

	/**
	 * 대상 종류와 ID를 요청이 연결된 경로 패턴에서 정한다. 가장 안쪽의 "이름/{ID}"를 대상으로 보고, ID가 없으면 첫 이름을 쓴다.
	 * 예: /api/v1/admin/products/{productId}/skus/{skuId}/stock-adjustments → skus, 옵션 ID
	 * /api/v1/admin/products/{productId}/skus → products, 상품 ID / /api/v1/uploads/complete → uploads
	 */
	static Target target(String pattern, Map<String, String> variables) {
		String[] segments = pattern.replaceFirst("^/api/v1/(admin/)?", "").split("/");
		String type = segments[0];
		String id = null;
		for (int i = 1; i < segments.length; i++) {
			if (segments[i].startsWith("{") && segments[i].endsWith("}")) {
				type = segments[i - 1];
				id = variables.get(segments[i].substring(1, segments[i].length() - 1));
			}
		}
		return new Target(type, id != null && id.length() <= 100 ? id : null);
	}

	record Target(String type, String id) {
	}

	private boolean isAuditedPath(String path) {
		// 이미지 업로드는 서버를 거치는 업로드와 직접 업로드의 완료 요청을 남긴다.
		return path.startsWith("/api/v1/admin/") || "/api/v1/uploads".equals(path)
				|| "/api/v1/uploads/complete".equals(path);
	}

	/** 감사 대상 요청인지: 관리자 API(로그인 제외)와 이미지 업로드의 변경 요청 */
	boolean isAudited(HttpServletRequest request) {
		String path = request.getRequestURI();
		return WRITE_METHODS.contains(request.getMethod()) && !path.startsWith(ADMIN_AUTH_PATH) && isAuditedPath(path);
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
