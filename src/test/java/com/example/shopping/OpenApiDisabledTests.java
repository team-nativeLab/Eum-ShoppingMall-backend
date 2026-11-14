package com.example.shopping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** API 문서는 API_DOCS_ENABLED=true일 때만 연다. 기본 설정(운영)에서는 문서 주소가 없다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDisabledTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 기본_설정에서는_API_문서가_열리지_않는다() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mockMvc.perform(get("/v3/api-docs/shop")).andExpect(status().isNotFound());
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}
}
