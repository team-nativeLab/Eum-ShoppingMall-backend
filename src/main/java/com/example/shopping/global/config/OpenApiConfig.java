package com.example.shopping.global.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.providers.JavadocProvider;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.security.ApiPolicy;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * API 문서(springdoc, Swagger UI: /swagger-ui/index.html). API_DOCS_ENABLED=true일 때만 연다(기본 꺼짐).
 * 웹·앱·관리자는 로그인과 토큰을 주고받는 방식이 달라 문서를 그룹으로 나누고, 그룹마다 로그인 방법을 안내한다(OpenApiGuide).
 * 로그인이 필요한 API에는 Bearer 토큰 자물쇠를 달고, ApiPolicy의 공개 API는 토큰 없음(인증 API)이나 토큰 선택(공개 조회·접수)으로 표시한다.
 * 요약·설명은 컨트롤러와 DTO의 Javadoc에서 가져오고(therapi-runtime-javadoc), 태그 설명은 컨트롤러 클래스 Javadoc이다.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

	/** 보안 스키마 이름. Swagger UI의 Authorize 창에 보인다. */
	static final String ACCESS_TOKEN = "accessToken";

	/** 실패 응답 스키마 이름 */
	static final String ERROR_RESPONSE = "ErrorResponse";

	static final String NO_TOKEN_NOTE = "로그인 없이 부른다. Access 토큰을 읽지 않는다.";
	static final String OPTIONAL_TOKEN_NOTE = "로그인 없이 부를 수 있다. 토큰을 붙이면 로그인한 회원으로 처리한다.";

	/** 웹 전용: Refresh 토큰을 쿠키로 주고받는 웹 로그인·재발급·로그아웃. 앱은 앱 인증 API(/api/v1/app/auth)를 쓴다. */
	static final List<String> WEB_ONLY = List.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout");

	/** 앱 전용: 앱 인증, 앱 구동 설정 */
	static final List<String> APP_ONLY = List.of("/api/v1/app/**");

	private static final String CUSTOMER_PATHS = "/api/v1/**";

	static {
		// 검색 조건·페이지를 개별 파라미터로 펼치면(springdoc.default-flat-param-object) 로그인 정보(@AuthenticationPrincipal Jwt)도
		// tokenValue 같은 쿼리 파라미터로 펼쳐진다. 요청 값이 아니므로 문서에서 뺀다.
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(Jwt.class);
	}

	/** 태그 이름별 설명(컨트롤러 클래스 Javadoc). 그룹 문서를 만들 때 API마다 채운다. */
	private final Map<String, String> tagDescriptions = new ConcurrentHashMap<>();

	@Bean
	OpenAPI shoppingOpenApi() {
		return new OpenAPI()
				.info(info("이음 쇼핑몰 API", OpenApiGuide.describe(OpenApiGuide.ALL)))
				// 상대 주소라 프록시(HTTPS 종료) 뒤에서도 문서를 연 주소로 호출한다.
				.servers(List.of(new Server().url("/").description("이 서버")))
				.components(new Components()
						.addSecuritySchemes(ACCESS_TOKEN, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("로그인 응답의 accessToken(15분)")))
				.addSecurityItem(new SecurityRequirement().addList(ACCESS_TOKEN));
	}

	/** 고객 웹(Next.js). 관리자 API와 앱 전용 API는 뺀다. */
	@Bean
	GroupedOpenApi webApi() {
		return GroupedOpenApi.builder()
				.group("web")
				.displayName("웹 API")
				.pathsToMatch(CUSTOMER_PATHS)
				.pathsToExclude(paths(ApiPolicy.ADMIN_API, APP_ONLY))
				.addOpenApiCustomizer(openApi -> openApi.info(info("이음 쇼핑몰 웹 API",
						OpenApiGuide.describe(OpenApiGuide.WEB))))
				.build();
	}

	/** 모바일 앱. 관리자 API와 쿠키를 쓰는 웹 로그인·재발급·로그아웃은 뺀다. */
	@Bean
	GroupedOpenApi appApi() {
		return GroupedOpenApi.builder()
				.group("app")
				.displayName("앱 API")
				.pathsToMatch(CUSTOMER_PATHS)
				.pathsToExclude(paths(ApiPolicy.ADMIN_API, WEB_ONLY))
				.addOpenApiCustomizer(openApi -> openApi.info(info("이음 쇼핑몰 앱 API",
						OpenApiGuide.describe(OpenApiGuide.APP))))
				.build();
	}

	/** 관리자 화면(관리자 로그인과 이미지 업로드 포함) */
	@Bean
	GroupedOpenApi adminApi() {
		return GroupedOpenApi.builder()
				.group("admin")
				.displayName("관리자 API")
				.pathsToMatch(paths(ApiPolicy.ADMIN_API))
				.addOpenApiCustomizer(openApi -> openApi.info(info("이음 쇼핑몰 관리자 API",
						OpenApiGuide.describe(OpenApiGuide.ADMIN))))
				.build();
	}

	/** 태그 설명으로 쓸 컨트롤러 클래스 Javadoc을 모은다. 모든 그룹에 적용한다. */
	@Bean
	GlobalOperationCustomizer tagDescriptionCollector(ObjectProvider<JavadocProvider> javadocProvider) {
		return (operation, handlerMethod) -> {
			JavadocProvider provider = javadocProvider.getIfAvailable();
			if (provider != null && operation.getTags() != null) {
				String description = provider.getClassJavadoc(handlerMethod.getBeanType());
				if (description != null && !description.isBlank()) {
					operation.getTags().forEach(tag -> tagDescriptions.putIfAbsent(tag, description.strip()));
				}
			}
			return operation;
		};
	}

	/** 공개 API 표시, 실패 응답, 태그 설명을 붙인다. 모든 그룹에 적용한다. */
	@Bean
	GlobalOpenApiCustomizer apiDocsCustomizer() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			TreeSet<String> usedTags = new TreeSet<>();
			openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
				markAccess(method.name(), path, operation);
				addErrorResponses(method.name(), path, operation);
				if (operation.getTags() != null) {
					usedTags.addAll(operation.getTags());
				}
			}));
			openApi.setTags(usedTags.stream()
					.map(name -> new Tag().name(name).description(tagDescriptions.get(name)))
					.toList());
			// 그룹 문서는 공통 OpenAPI의 스키마를 물려받지 않으므로, 실패 응답이 가리키는 스키마를 그룹마다 넣는다.
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			openApi.getComponents().addSchemas(ERROR_RESPONSE, errorResponseSchema());
		};
	}

	/**
	 * 인증 API(PUBLIC_AUTH)는 토큰을 읽지 않으므로 자물쇠를 없애고(security: []),
	 * 공개 조회(GET PUBLIC_GET)·공개 접수(POST PUBLIC_POST)는 토큰을 고를 수 있게 둔다(security: [{}, {accessToken: []}]).
	 * 나머지는 문서 전체의 기본값(토큰 필요)을 따른다.
	 */
	static void markAccess(String method, String path, Operation operation) {
		if (ApiPolicy.matches(ApiPolicy.PUBLIC_AUTH, path)) {
			operation.setSecurity(List.of());
			appendDescription(operation, NO_TOKEN_NOTE);
		} else if (("GET".equals(method) && ApiPolicy.matches(ApiPolicy.PUBLIC_GET, path))
				|| ("POST".equals(method) && ApiPolicy.matches(ApiPolicy.PUBLIC_POST, path))) {
			operation.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList(ACCESS_TOKEN)));
			appendDescription(operation, OPTIONAL_TOKEN_NOTE);
		}
	}

	/**
	 * API마다 일어날 수 있는 실패 응답을 붙인다. 입력이 있으면 400, 토큰이 필요하면 401, 관리자 전용이면 403,
	 * 경로에 대상 ID가 있으면 404, IP별 요청 제한(ApiPolicy.RATE_LIMITS)을 받으면 429. 이미 적힌 응답은 그대로 둔다.
	 */
	static void addErrorResponses(String method, String path, Operation operation) {
		ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
		boolean hasInput = operation.getRequestBody() != null
				|| (operation.getParameters() != null && !operation.getParameters().isEmpty());
		if (hasInput) {
			addError(responses, "400", "입력값이 올바르지 않음. errors에 항목별 사유가 있다(INVALID_INPUT 등).",
					ErrorCode.INVALID_INPUT, List.of(Map.of("field", "email", "reason", "이메일 형식이 올바르지 않습니다.")));
		}
		if (operation.getSecurity() == null) {
			addError(responses, "401", "로그인이 필요하거나 토큰이 만료·폐기됨(UNAUTHORIZED, TOKEN_EXPIRED)",
					ErrorCode.UNAUTHORIZED, null);
		}
		if (ApiPolicy.matches(ApiPolicy.ADMIN_API, path) && !ApiPolicy.matches(ApiPolicy.PUBLIC_AUTH, path)) {
			addError(responses, "403", "관리자 권한이 없음(FORBIDDEN)", ErrorCode.FORBIDDEN, null);
		}
		if (path.contains("{")) {
			addError(responses, "404", "대상을 찾을 수 없음. 코드는 대상마다 다르다(PRODUCT_NOT_FOUND 등).",
					ErrorCode.NOT_FOUND, null);
		}
		if (ApiPolicy.RATE_LIMITS.stream().anyMatch(rule -> rule.method().name().equals(method)
				&& ApiPolicy.matches(List.of(rule.pattern()), path))) {
			ApiResponse tooMany = error("요청이 너무 많음. Retry-After(초)만큼 기다렸다가 다시 보낸다(TOO_MANY_REQUESTS).",
					ErrorCode.TOO_MANY_REQUESTS, null);
			tooMany.addHeaderObject("Retry-After", new Header().description("다시 보낼 수 있을 때까지 남은 초")
					.schema(new IntegerSchema()));
			responses.putIfAbsent("429", tooMany);
		}
		operation.setResponses(responses);
	}

	private static void addError(ApiResponses responses, String status, String description, ErrorCode code,
			List<Map<String, String>> errors) {
		responses.putIfAbsent(status, error(description, code, errors));
	}

	private static ApiResponse error(String description, ErrorCode code, List<Map<String, String>> errors) {
		Map<String, Object> example = new LinkedHashMap<>();
		example.put("success", false);
		example.put("code", code.name());
		example.put("message", code.getMessage());
		example.put("data", null);
		example.put("errors", errors);
		example.put("timestamp", "2026-11-14T12:00:00+09:00");
		return new ApiResponse().description(description).content(new Content().addMediaType(
				org.springframework.http.MediaType.APPLICATION_JSON_VALUE, new MediaType()
						.schema(new Schema<>().$ref("#/components/schemas/" + ERROR_RESPONSE))
						.example(example)));
	}

	/** 실패 응답(ApiResponse의 실패 모양) */
	private static Schema<?> errorResponseSchema() {
		return new ObjectSchema()
				.description("실패 응답. 화면은 code로 분기한다.")
				.addProperty("success", new BooleanSchema().example(false))
				.addProperty("code", new StringSchema().description("오류 코드(문서 첫 화면의 오류 코드 표 참고)")
						.example("INVALID_INPUT"))
				.addProperty("message", new StringSchema().description("사용자에게 보여 줄 안내 문구"))
				.addProperty("errors", new ArraySchema()
						.description("입력값 검증 실패(400 INVALID_INPUT)일 때만 있는 항목별 사유")
						.items(new ObjectSchema()
								.addProperty("field", new StringSchema().description("요청 항목 이름"))
								.addProperty("reason", new StringSchema().description("사유"))))
				.addProperty("timestamp", new StringSchema().format("date-time"));
	}

	private static void appendDescription(Operation operation, String note) {
		String description = operation.getDescription();
		operation.setDescription(description == null || description.isBlank() ? note : description + "\n\n" + note);
	}

	private static Info info(String title, String description) {
		return new Info().title(title).version("v1").description(description);
	}

	@SafeVarargs
	private static String[] paths(List<String>... groups) {
		return Stream.of(groups).flatMap(List::stream).filter(Objects::nonNull).toArray(String[]::new);
	}
}
