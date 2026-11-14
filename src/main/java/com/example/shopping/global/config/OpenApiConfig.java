package com.example.shopping.global.config;

import java.util.List;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.shopping.global.security.ApiPolicy;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * API 문서(springdoc, Swagger UI: /swagger-ui/index.html). API_DOCS_ENABLED=true일 때만 연다(기본 꺼짐).
 * 로그인이 필요한 API에는 Bearer 토큰 자물쇠를 달고, ApiPolicy의 공개 API는 토큰 없음(인증 API)이나 토큰 선택(공개 조회·접수)으로 표시한다.
 * 요약·설명은 컨트롤러와 DTO의 Javadoc에서 가져온다(therapi-runtime-javadoc).
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

	/** 보안 스키마 이름. Swagger UI의 Authorize 창에 보인다. */
	static final String ACCESS_TOKEN = "accessToken";

	static final String NO_TOKEN_NOTE = "로그인 없이 부른다. Access 토큰을 읽지 않는다.";
	static final String OPTIONAL_TOKEN_NOTE = "로그인 없이 부를 수 있다. 토큰을 붙이면 로그인한 회원으로 처리한다.";

	static {
		// 검색 조건·페이지를 개별 파라미터로 펼치면(springdoc.default-flat-param-object) 로그인 정보(@AuthenticationPrincipal Jwt)도
		// tokenValue 같은 쿼리 파라미터로 펼쳐진다. 요청 값이 아니므로 문서에서 뺀다.
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(Jwt.class);
	}

	private static final String DESCRIPTION = """
			이음 쇼핑몰 백엔드 API입니다. 오른쪽 위에서 **고객 API**와 **관리자 API**를 고릅니다.

			### 인증
			1. 회원은 `POST /api/v1/auth/login`, 관리자는 `POST /api/v1/admin/auth/login`으로 로그인합니다. \
			관리자 OTP를 켰으면 `POST /api/v1/admin/auth/mfa/verify`까지 거칩니다.
			2. 응답의 `data.accessToken`을 **Authorize**에 넣으면 이후 요청에 `Authorization: Bearer …`가 붙습니다. \
			Access 토큰은 15분 동안 쓸 수 있고, 새로 고치면 다시 넣어야 합니다.
			3. 설명에 "로그인 없이"라고 적힌 API는 토큰 없이 부를 수 있습니다. 공개 조회·접수 API에 토큰을 붙이면 \
			로그인한 회원으로 처리하며, 만료된 토큰을 붙이면 401입니다.

			### 응답 형식
			- 성공: `{"success": true, "code": "SUCCESS", "message": "OK", "data": …}`
			- 실패: `{"success": false, "code": "오류 코드", "message": "안내 문구", "errors": [{"field": "…", "reason": "…"}]}`. \
			화면은 `code`로 분기합니다. `errors`는 입력값 검증(400 INVALID_INPUT)에서만 채웁니다.

			### 요청 제한
			로그인·인증번호·문의 접수·주문·결제 등은 IP별 분당 요청 수를 제한합니다. 넘으면 429와 `Retry-After`(초)를 돌려줍니다.
			""";

	@Bean
	OpenAPI shoppingOpenApi() {
		return new OpenAPI()
				.info(new Info().title("이음 쇼핑몰 API").version("v1").description(DESCRIPTION))
				// 상대 주소라 프록시(HTTPS 종료) 뒤에서도 문서를 연 주소로 호출한다.
				.servers(List.of(new Server().url("/").description("이 서버")))
				.components(new Components().addSecuritySchemes(ACCESS_TOKEN, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("로그인 응답의 data.accessToken(15분)")))
				.addSecurityItem(new SecurityRequirement().addList(ACCESS_TOKEN));
	}

	/** 고객 화면이 쓰는 API. 관리자 API(ApiPolicy.ADMIN_API)는 뺀다. */
	@Bean
	GroupedOpenApi shopApi() {
		return GroupedOpenApi.builder()
				.group("shop")
				.displayName("고객 API")
				.pathsToMatch("/api/v1/**")
				.pathsToExclude(ApiPolicy.ADMIN_API.toArray(String[]::new))
				.build();
	}

	/** 관리자 화면이 쓰는 API(관리자 로그인 포함) */
	@Bean
	GroupedOpenApi adminApi() {
		return GroupedOpenApi.builder()
				.group("admin")
				.displayName("관리자 API")
				.pathsToMatch(ApiPolicy.ADMIN_API.toArray(String[]::new))
				.build();
	}

	/** 공개 API의 자물쇠를 ApiPolicy(보안 설정이 읽는 표)에 맞춘다. 모든 그룹에 적용한다. */
	@Bean
	GlobalOpenApiCustomizer publicApiCustomizer() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().forEach((path, item) -> item.readOperationsMap()
					.forEach((method, operation) -> markAccess(method.name(), path, operation)));
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

	private static void appendDescription(Operation operation, String note) {
		String description = operation.getDescription();
		operation.setDescription(description == null || description.isBlank() ? note : description + "\n\n" + note);
	}
}
