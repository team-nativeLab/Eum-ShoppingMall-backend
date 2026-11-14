package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
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

/** API 문서(springdoc). 문서를 켠 설정으로 띄워 인증 표시·그룹·태그·요약을 확인한다. */
@SpringBootTest(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTests {

	private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 로그인이_필요한_API는_Bearer_토큰을_요구하고_공개_API는_ApiPolicy대로_표시한다() throws Exception {
		mockMvc.perform(get("/v3/api-docs/shop"))
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
	void 고객_API와_관리자_API를_나눠_보여_준다() throws Exception {
		Map<String, Object> shop = paths("shop");
		Map<String, Object> admin = paths("admin");

		assertThat(shop).containsKeys("/api/v1/products", "/api/v1/home", "/api/v1/events")
				.doesNotContainKeys("/api/v1/admin/products", "/api/v1/uploads");
		assertThat(admin).containsKeys("/api/v1/admin/products", "/api/v1/admin/auth/login", "/api/v1/uploads")
				.doesNotContainKeys("/api/v1/products");
		mockMvc.perform(get("/v3/api-docs/swagger-config"))
				.andExpect(jsonPath("$.urls[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("고객 API",
						"관리자 API")));
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	/** 새 API를 추가하면 컨트롤러에 @Tag(한국어 이름)를 달고 메서드에 Javadoc을 적는다. */
	@Test
	void 모든_API에_한국어_태그와_요약이_있다() throws Exception {
		List<String> missing = new ArrayList<>();
		for (String group : List.of("shop", "admin")) {
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
					missing.add(method.toUpperCase() + " " + path + " tags=" + tags + " summary=" + summary);
				}
			}));
		}
		assertThat(missing).isEmpty();
	}

	@Test
	void Javadoc을_요약과_설명으로_쓴다() throws Exception {
		mockMvc.perform(get("/v3/api-docs/shop"))
				.andExpect(jsonPath("$.paths['/api/v1/products/recommended'].get.summary")
						.value(org.hamcrest.Matchers.startsWith("검색 결과가 없을 때의 추천 상품")))
				.andExpect(jsonPath("$.paths['/api/v1/products/recommended'].get.description")
						.value(org.hamcrest.Matchers.containsString("로그인 없이 부를 수 있다")));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> paths(String group) throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs/{group}", group)).andReturn().getResponse()
				.getContentAsString();
		return (Map<String, Object>) JsonPath.read(json, "$.paths");
	}
}
