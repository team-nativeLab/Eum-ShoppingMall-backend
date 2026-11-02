package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.shopping.global.security.ApiPolicy;

/**
 * API 정책 표(ApiPolicy)가 실제 엔드포인트와 맞는지 확인한다 (#102).
 * API를 추가하거나 경로를 바꿨는데 표를 고치지 않으면 여기서 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiPolicyTests {

	/** 컨트롤러가 아닌 곳이 처리하는 경로(업로드한 이미지 파일) */
	private static final List<String> NON_CONTROLLER_PATHS = List.of("/files/**");

	/**
	 * 로그인 없이 부르지만 요청 제한을 두지 않는 변경 요청.
	 * 로그아웃은 쿠키의 Refresh 토큰을 폐기할 뿐이고, PG 웹훅은 PG 서버가 보내므로 IP별로 막으면 정상 알림까지 막힌다.
	 */
	private static final List<String> UNLIMITED_PUBLIC_WRITES = List.of("/api/v1/auth/logout",
			"/api/v1/payments/webhook");

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void 관리자_기능은_관리자만_부르고_변경_요청은_감사_로그에_남긴다() {
		List<String> violations = endpoints().stream()
				.filter(e -> !e.pattern().equals(ApiPolicy.ADMIN_LOGIN))
				.filter(e -> {
					boolean adminController = e.controller().startsWith("Admin");
					boolean adminPath = e.pattern().startsWith("/api/v1/admin/");
					boolean adminOnly = ApiPolicy.matches(ApiPolicy.ADMIN_API, e.samplePath());
					boolean unaudited = e.isWrite() && !ApiPolicy.isAudited(e.method(), e.samplePath());
					// 관리자 컨트롤러가 /admin 밖에 있거나, /admin 경로를 일반 컨트롤러가 쓰거나, 관리자 변경이 감사에서 빠지면 실패
					return (adminController && (!adminOnly || unaudited)) || (adminPath && !adminController);
				})
				.map(Endpoint::toString)
				.toList();

		assertThat(violations).isEmpty();
	}

	@Test
	void 정책_표의_경로와_요청_제한_규칙은_모두_실제_API를_가리킨다() {
		List<Endpoint> endpoints = endpoints();
		Map<String, List<String>> tables = new LinkedHashMap<>();
		tables.put("PUBLIC_AUTH", ApiPolicy.PUBLIC_AUTH);
		tables.put("PUBLIC_GET", ApiPolicy.PUBLIC_GET);
		tables.put("PUBLIC_POST", ApiPolicy.PUBLIC_POST);
		tables.put("ADMIN_API", ApiPolicy.ADMIN_API);
		tables.put("AUDIT_EXCLUDED", ApiPolicy.AUDIT_EXCLUDED);

		List<String> stale = new ArrayList<>();
		tables.forEach((table, patterns) -> patterns.stream()
				.filter(pattern -> !NON_CONTROLLER_PATHS.contains(pattern))
				.filter(pattern -> endpoints.stream().noneMatch(e -> e.matches(pattern)
						&& (!table.equals("PUBLIC_GET") || e.method().equals("GET"))
						&& (!table.equals("PUBLIC_POST") || e.method().equals("POST"))))
				.forEach(pattern -> stale.add(table + " " + pattern)));
		ApiPolicy.RATE_LIMITS.stream()
				.filter(rule -> endpoints.stream()
						.noneMatch(e -> e.method().equals(rule.method().name()) && e.matches(rule.pattern())))
				.forEach(rule -> stale.add("RATE_LIMITS " + rule.method() + " " + rule.pattern()));

		assertThat(stale).isEmpty();
	}

	@Test
	void 로그인_없이_부르는_변경_요청은_요청_제한을_받는다() {
		List<String> unlimited = endpoints().stream()
				.filter(Endpoint::isWrite)
				.filter(e -> ApiPolicy.matches(ApiPolicy.PUBLIC_AUTH, e.samplePath())
						|| (e.method().equals("POST") && ApiPolicy.matches(ApiPolicy.PUBLIC_POST, e.samplePath())))
				.filter(e -> !UNLIMITED_PUBLIC_WRITES.contains(e.pattern()))
				.filter(e -> ApiPolicy.RATE_LIMITS.stream()
						.noneMatch(rule -> rule.method().name().equals(e.method()) && e.matches(rule.pattern())))
				.map(Endpoint::toString)
				.toList();

		assertThat(unlimited).isEmpty();
	}

	private List<Endpoint> endpoints() {
		List<Endpoint> endpoints = new ArrayList<>();
		handlerMapping.getHandlerMethods().forEach((info, handler) -> {
			String controller = handler.getBeanType().getSimpleName();
			for (String pattern : info.getPatternValues()) {
				for (RequestMethod method : info.getMethodsCondition().getMethods()) {
					endpoints.add(new Endpoint(method.name(), pattern, controller));
				}
			}
		});
		assertThat(endpoints).isNotEmpty();
		return endpoints;
	}

	private record Endpoint(String method, String pattern, String controller) {

		/** 경로 변수를 채운 예시 경로. 정책 표의 패턴과 비교할 때 쓴다. */
		String samplePath() {
			return pattern.replaceAll("\\{[^}]+}", "1");
		}

		boolean matches(String policyPattern) {
			return ApiPolicy.matches(List.of(policyPattern), samplePath());
		}

		boolean isWrite() {
			return !method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS");
		}

		@Override
		public String toString() {
			return method + " " + pattern + " (" + controller + ")";
		}
	}
}
