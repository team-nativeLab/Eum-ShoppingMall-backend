package com.example.shopping.global.security;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * API별 공개 여부·요청 제한·감사 대상을 한 곳에 둔다. 보안 설정(SecurityConfig), IP별 요청 제한(RateLimitFilter),
 * 관리자 감사 기록(AdminAuditInterceptor)이 모두 이 표를 읽는다. API를 추가하거나 경로를 바꾸면 여기만 고치고,
 * ApiPolicyTests가 실제 엔드포인트와 맞는지(관리자 기능이 관리자 전용·감사 대상인지, 지운 경로가 남지 않았는지) 확인한다.
 * 경로는 Spring 경로 패턴으로 적는다(* = 경로 한 칸, ** = 여러 칸). 표에 없는 API는 로그인한 회원만 부를 수 있다.
 */
public final class ApiPolicy {

	public static final String ADMIN_LOGIN = "/api/v1/admin/auth/login";

	/**
	 * 로그인 없이 부르는 인증 API. 이 경로에서는 Access 토큰을 읽지 않아, 프론트가 만료된 토큰을 붙인 채
	 * 로그인·재발급해도 토큰 오류(401)로 막히지 않는다.
	 * 로그아웃도 여기에 둔다. 로그아웃은 쿠키의 Refresh 토큰만 폐기하므로 Access 토큰이 필요 없고,
	 * Access 토큰이 만료된 뒤(15분) 누른 로그아웃이 401로 막히면 Refresh 토큰이 남아 그 브라우저로 로그인 상태를 되살릴 수 있다.
	 */
	public static final List<String> PUBLIC_AUTH = List.of(
			"/api/v1/auth/signup",
			"/api/v1/auth/login",
			"/api/v1/auth/refresh",
			"/api/v1/auth/logout",
			"/api/v1/auth/email-verifications",
			"/api/v1/auth/email-verifications/confirm",
			"/api/v1/auth/password-reset",
			"/api/v1/auth/password-reset/confirm",
			ADMIN_LOGIN);

	/** 로그인 없이 조회하는 공개 데이터(GET): 메인, 카테고리, 상품, 공지, FAQ, 고정 콘텐츠, 앱 설정, 배송비 정책, 업로드된 이미지 */
	public static final List<String> PUBLIC_GET = List.of(
			"/files/**",
			"/api/v1/home",
			"/api/v1/app/config",
			"/api/v1/shipping-policy",
			"/api/v1/contents/**",
			"/api/v1/categories/**",
			"/api/v1/products/**",
			"/api/v1/notices/**",
			"/api/v1/faqs/**");

	/**
	 * 로그인 없이 보내는 요청(POST). 견적 문의·1:1 문의는 비회원도 접수하고, 같은 경로의 GET(내 내역)은 로그인이 필요하다.
	 * PG 웹훅은 저장한 비밀값과 PG 재조회로 확인한다.
	 */
	public static final List<String> PUBLIC_POST = List.of(
			"/api/v1/quotes",
			"/api/v1/support/inquiries",
			"/api/v1/payments/webhook");

	/** 관리자 API. 이미지 업로드는 /admin 밖에 있지만 관리자만 쓴다. */
	public static final List<String> ADMIN_API = List.of(
			"/api/v1/admin/**",
			"/api/v1/uploads",
			"/api/v1/uploads/**");

	/**
	 * 관리자 API의 변경 요청 중 감사 로그에 남기지 않는 것. 관리자 로그인은 AuthService가 성공·실패를 따로 남기고(본문에 비밀번호가 있다),
	 * 직접 업로드 주소 발급은 아무것도 바꾸지 않는다(업로드 완료 요청을 남긴다).
	 */
	public static final List<String> AUDIT_EXCLUDED = List.of(
			ADMIN_LOGIN,
			"/api/v1/uploads/presigned-url");

	/** 로그인 없이 여는 운영 경로. API 문서는 켰을 때만 있다(API_DOCS_ENABLED). */
	public static final List<String> PUBLIC_OPERATIONS = List.of(
			"/error",
			"/actuator/health/**",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/v3/api-docs/**");

	/** 헬스 체크 외의 운영 엔드포인트는 켜더라도 관리자만 본다. */
	public static final List<String> ADMIN_OPERATIONS = List.of("/actuator/**");

	/** 요청 제한 한도. DEFAULT는 security.rate-limit.requests-per-minute, 나머지는 public-product-requests-per-minute(자동 완성은 그 두 배) */
	public enum Limit {
		DEFAULT, PUBLIC_READ, AUTOCOMPLETE
	}

	/** @param bucket 같은 bucket끼리 함께 센다. 주문번호처럼 경로 값이 달라도 같은 API면 한 묶음이다. */
	public record RateLimit(HttpMethod method, String pattern, String bucket, Limit limit) {
	}

	/**
	 * IP별 분당 요청 제한. 위에서부터 처음 맞는 규칙 하나만 적용한다.
	 * - 로그인 없이 호출하거나 비밀번호를 검사하는 API: 비밀번호 대입, 가입·문의 도배, 해시 연산을 노린 부하 공격 대비
	 * - 주문·결제·취소: 호출마다 PG사 API나 재고 변경이 따라오므로 반복 호출로 PG 호출 한도를 소진시키지 못하게 한다.
	 */
	public static final List<RateLimit> RATE_LIMITS = List.of(
			// 회원·관리자 로그인은 한 묶음으로 세어 경로를 바꿔 한도를 피하지 못하게 한다.
			new RateLimit(HttpMethod.POST, "/api/v1/auth/login", "POST /api/v1/login", Limit.DEFAULT),
			new RateLimit(HttpMethod.POST, ADMIN_LOGIN, "POST /api/v1/login", Limit.DEFAULT),
			post("/api/v1/auth/signup"),
			post("/api/v1/auth/refresh"),
			// 인증번호 발송(메일 비용·스팸)과 확인(번호 대입)
			post("/api/v1/auth/email-verifications"),
			post("/api/v1/auth/email-verifications/confirm"),
			post("/api/v1/auth/password-reset"),
			post("/api/v1/auth/password-reset/confirm"),
			new RateLimit(HttpMethod.PATCH, "/api/v1/me/password", "PATCH /api/v1/me/password", Limit.DEFAULT),
			post("/api/v1/quotes"),
			post("/api/v1/support/inquiries"),
			post("/api/v1/orders"),
			post("/api/v1/orders/*/cancel"),
			post("/api/v1/payments/confirm"),
			post("/api/v1/uploads/presigned-url"),
			post("/api/v1/uploads"),
			// 공개 상품 조회는 화면 기능별로 따로 센다. 검색 한 번에 목록·필터 개수를 함께 부르고,
			// 자동 완성은 글자를 칠 때마다 부르므로 한 묶음으로 세면 한 사무실(같은 공인 IP) 직원 몇 명만으로도 막힌다.
			// 더 구체적인 경로를 먼저 둔다.
			new RateLimit(HttpMethod.GET, "/api/v1/products/suggestions", "GET /api/v1/products/suggestions",
					Limit.AUTOCOMPLETE),
			new RateLimit(HttpMethod.GET, "/api/v1/products/facets", "GET /api/v1/products/facets", Limit.PUBLIC_READ),
			new RateLimit(HttpMethod.GET, "/api/v1/products", "GET /api/v1/products", Limit.PUBLIC_READ),
			new RateLimit(HttpMethod.GET, "/api/v1/products/**", "GET /api/v1/products/{productId}", Limit.PUBLIC_READ));

	private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
	private static final List<PathPattern> AUDITED_PATTERNS = parse(ADMIN_API);
	private static final List<PathPattern> AUDIT_EXCLUDED_PATTERNS = parse(AUDIT_EXCLUDED);

	private ApiPolicy() {
	}

	/** 감사 대상: 관리자 API의 변경 요청(POST·PUT·PATCH·DELETE) 중 AUDIT_EXCLUDED에 없는 것 */
	public static boolean isAudited(String method, String path) {
		PathContainer container = PathContainer.parsePath(path);
		return WRITE_METHODS.contains(method) && matches(AUDITED_PATTERNS, container)
				&& !matches(AUDIT_EXCLUDED_PATTERNS, container);
	}

	/** path가 patterns 중 하나와 맞는지 */
	public static boolean matches(List<String> patterns, String path) {
		return matches(parse(patterns), PathContainer.parsePath(path));
	}

	private static boolean matches(List<PathPattern> patterns, PathContainer path) {
		return patterns.stream().anyMatch(pattern -> pattern.matches(path));
	}

	private static List<PathPattern> parse(List<String> patterns) {
		return patterns.stream().map(PathPatternParser.defaultInstance::parse).toList();
	}

	private static RateLimit post(String pattern) {
		return new RateLimit(HttpMethod.POST, pattern, "POST " + pattern, Limit.DEFAULT);
	}
}
