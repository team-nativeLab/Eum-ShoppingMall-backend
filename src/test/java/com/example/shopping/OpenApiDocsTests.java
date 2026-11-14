package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** API 문서(springdoc). 문서를 켠 설정으로 띄워 웹·앱·관리자 그룹, 인증 표시, 실패 응답, 태그·요약을 확인한다. */
@SpringBootTest(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTests {

	private static final List<String> GROUPS = List.of("web", "app", "admin");
	private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 로그인이_필요한_API는_Bearer_토큰을_요구하고_공개_API는_ApiPolicy대로_표시한다() throws Exception {
		mockMvc.perform(get("/v3/api-docs/web"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.accessToken.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.securitySchemes.accessToken.bearerFormat").value("JWT"))
				.andExpect(jsonPath("$.security[0].accessToken").isArray())
				// 인증 API는 토큰을 읽지 않는다.
				.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
				// 공개 조회는 토큰을 고를 수 있다(없음 또는 accessToken).
				.andExpect(jsonPath("$.paths['/api/v1/products/{productId}'].get.security.length()").value(2))
				.andExpect(jsonPath("$.paths['/api/v1/products/{productId}'].get.security[1].accessToken").isArray())
				// 같은 경로라도 POST(공개 접수)와 GET(내 내역, 로그인 필요)이 다르다.
				.andExpect(jsonPath("$.paths['/api/v1/quotes'].post.security.length()").value(2))
				.andExpect(jsonPath("$.paths['/api/v1/quotes'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/me'].get.security").doesNotExist());
	}

	@Test
	void 웹_앱_관리자_API를_나눠_보여_주고_그룹마다_로그인_방법을_안내한다() throws Exception {
		Map<String, Object> web = paths("web");
		Map<String, Object> app = paths("app");
		Map<String, Object> admin = paths("admin");

		// 웹은 쿠키로 Refresh 토큰을 주고받는 웹 로그인을, 앱은 앱 전용 API를 쓴다. 상품 등 나머지는 함께 쓴다.
		assertThat(web).containsKeys("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/signup",
				"/api/v1/products").doesNotContainKeys("/api/v1/app/config", "/api/v1/admin/products");
		assertThat(app).containsKeys("/api/v1/app/config", "/api/v1/auth/signup", "/api/v1/products")
				.doesNotContainKeys("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
						"/api/v1/admin/products");
		assertThat(admin).containsKeys("/api/v1/admin/products", "/api/v1/admin/auth/login", "/api/v1/uploads")
				.doesNotContainKeys("/api/v1/products");

		mockMvc.perform(get("/v3/api-docs/web"))
				.andExpect(jsonPath("$.info.title").value("이음 쇼핑몰 웹 API"))
				.andExpect(jsonPath("$.info.description").value(containsString("refresh_token")));
		mockMvc.perform(get("/v3/api-docs/app"))
				.andExpect(jsonPath("$.info.description").value(containsString("/api/v1/app/auth/login")))
				.andExpect(jsonPath("$.info.description").value(containsString("Keychain")));
		mockMvc.perform(get("/v3/api-docs/admin"))
				.andExpect(jsonPath("$.info.description").value(containsString("admin_refresh_token")))
				// 오류 코드 표는 ErrorCode에서 만든다.
				.andExpect(jsonPath("$.info.description").value(containsString("| `LOGIN_LOCKED` | 429 |")));
		mockMvc.perform(get("/v3/api-docs/swagger-config"))
				.andExpect(jsonPath("$.urls[*].name").value(containsInAnyOrder("웹 API", "앱 API", "관리자 API")));
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	@Test
	void API마다_일어날_수_있는_실패_응답을_적는다() throws Exception {
		mockMvc.perform(get("/v3/api-docs/web"))
				.andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code").exists())
				// 토큰이 필요한 API는 401
				.andExpect(jsonPath("$.paths['/api/v1/me'].get.responses['401'].content['application/json'].schema['$ref']")
						.value("#/components/schemas/ErrorResponse"))
				.andExpect(jsonPath("$.paths['/api/v1/me'].get.responses['401'].content['application/json'].example.code")
						.value("UNAUTHORIZED"))
				// 토큰을 읽지 않는 로그인은 401 대신 입력 오류(400)와 요청 제한(429, Retry-After)
				.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['400']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['429'].headers['Retry-After']").exists())
				// 대상 ID가 있는 경로는 404
				.andExpect(jsonPath("$.paths['/api/v1/products/{productId}'].get.responses['404']").exists())
				// 본문 형식은 */* 대신 application/json
				.andExpect(jsonPath("$.paths['/api/v1/me'].get.responses['200'].content['application/json']").exists());
		mockMvc.perform(get("/v3/api-docs/admin"))
				.andExpect(jsonPath("$.paths['/api/v1/admin/products'].get.responses['403'].content['application/json'].example.code")
						.value("FORBIDDEN"))
				.andExpect(jsonPath("$.paths['/api/v1/admin/auth/login'].post.responses['403']").doesNotExist());
	}

	/** 새 API를 추가하면 컨트롤러에 @Tag(한국어 이름)와 클래스 Javadoc(태그 설명)을 달고 메서드에 Javadoc을 적는다. */
	@Test
	void 모든_API에_한국어_태그와_요약이_있고_모든_태그에_설명이_있다() throws Exception {
		List<String> missing = new ArrayList<>();
		for (String group : GROUPS) {
			String json = mockMvc.perform(get("/v3/api-docs/{group}", group)).andReturn().getResponse()
					.getContentAsString();
			Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(json, "$.paths");
			paths.forEach((path, item) -> item.forEach((method, operation) -> {
				if (!METHODS.contains(method)) {
					return;
				}
				@SuppressWarnings("unchecked")
				List<String> tags = (List<String>) operation.get("tags");
				boolean koreanTag = tags != null && tags.stream().anyMatch(tag -> tag.matches(".*[가-힣].*"));
				Object summary = operation.get("summary");
				if (!koreanTag || summary == null || summary.toString().isBlank()) {
					missing.add(group + " " + method.toUpperCase() + " " + path + " tags=" + tags + " summary=" + summary);
				}
			}));
			List<Map<String, Object>> tags = JsonPath.read(json, "$.tags");
			tags.stream()
					.filter(tag -> tag.get("description") == null || tag.get("description").toString().isBlank())
					.forEach(tag -> missing.add(group + " 태그 설명 없음: " + tag.get("name")));
		}
		assertThat(missing).isEmpty();
	}

	@Test
	void Javadoc을_요약과_설명으로_쓴다() throws Exception {
		mockMvc.perform(get("/v3/api-docs/web"))
				.andExpect(jsonPath("$.paths['/api/v1/products/suggestions'].get.summary")
						.value(startsWith("검색창 자동 완성")))
				.andExpect(jsonPath("$.paths['/api/v1/products/suggestions'].get.description")
						.value(containsString("로그인 없이 부를 수 있다")))
				.andExpect(jsonPath("$.tags[?(@.name == '배송지')].description")
						.value(org.hamcrest.Matchers.hasItem(containsString("주소록"))));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> paths(String group) throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs/{group}", group)).andReturn().getResponse()
				.getContentAsString();
		return (Map<String, Object>) JsonPath.read(json, "$.paths");
	}
}
