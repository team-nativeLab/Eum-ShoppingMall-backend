package com.example.shopping.domain.audit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.example.shopping.domain.audit.service.AdminAuditLogService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** 감사 로그를 저장하지 못했을 때 남기는 오류 로그에 요청 본문(개인정보)이 없는지 확인한다 (#139). */
@ExtendWith(OutputCaptureExtension.class)
class AdminAuditInterceptorTests {

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 감사_로그_저장에_실패하면_본문_없이_대상만_오류_로그로_남긴다(CapturedOutput output) throws Exception {
		AdminAuditLogService auditLogService = mock(AdminAuditLogService.class);
		doThrow(new IllegalStateException("db down")).when(auditLogService)
				.record(any(), any(), any(), any(), any(), any(), any(), anyInt());
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		AdminAuditInterceptor interceptor = new AdminAuditInterceptor(auditLogService, meterRegistry);

		MockHttpServletRequest raw = new MockHttpServletRequest("PATCH", "/api/v1/admin/users/42/status");
		raw.setContentType("application/json");
		raw.setContent("{\"status\":\"SUSPENDED\",\"reason\":\"홍길동 010-1234-5678 욕설\"}"
				.getBytes(StandardCharsets.UTF_8));
		raw.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/admin/users/{userId}/status");
		raw.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("userId", "42"));
		ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(raw, 10_000);
		request.getInputStream().readAllBytes();
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("7")
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
		SecurityContextHolder.getContext()
				.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
		Method method = AdminAuditInterceptorTests.class.getDeclaredMethod("handler");

		interceptor.afterCompletion(request, new MockHttpServletResponse(), new HandlerMethod(this, method), null);

		assertThat(output).contains("관리자 변경 감사 로그 저장 실패", "target=users:42")
				.doesNotContain("홍길동", "010-1234-5678", "SUSPENDED");
		assertThat(meterRegistry.counter("admin.audit.record.failures").count()).isEqualTo(1);
	}

	@SuppressWarnings("unused")
	private void handler() {
	}
}
