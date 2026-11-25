package com.example.shopping;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** FAQ 노출 여부·관리자 FAQ 목록과 공지 정렬 순서(#220). 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class SupportContentApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 숨긴_FAQ는_고객_목록에_나오지_않고_관리자_목록에는_정렬_순서대로_나온다() throws Exception {
		postJson("/api/v1/admin/faqs", """
				{"category":"배송","question":"언제 도착하나요?","answer":"2~3일 걸립니다.","sortOrder":2}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.visible").value(true));
		long hidden = idOf(postJson("/api/v1/admin/faqs", """
				{"category":"교환","question":"교환할 수 있나요?","answer":"준비 중입니다.","sortOrder":1,"visible":false}
				""")
				.andExpect(jsonPath("$.data.visible").value(false)));

		mockMvc.perform(get("/api/v1/faqs").with(anonymous()))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].question").value("언제 도착하나요?"))
				.andExpect(jsonPath("$.data[0].visible").doesNotExist());
		mockMvc.perform(get("/api/v1/admin/faqs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].id").value(hidden))
				.andExpect(jsonPath("$.data[0].visible").value(false));

		// 보낸 항목만 바꾸므로 노출 여부만 바꿀 수 있다.
		patchJson("/api/v1/admin/faqs/" + hidden, "{\"visible\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.question").value("교환할 수 있나요?"))
				.andExpect(jsonPath("$.data.visible").value(true));
		mockMvc.perform(get("/api/v1/faqs").with(anonymous()))
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].question").value("교환할 수 있나요?"));
	}

	@Test
	void 공지는_고정_공지_먼저_정렬_순서대로_나온다() throws Exception {
		postJson("/api/v1/admin/notices", """
				{"title":"배송 지연 안내","content":"내용","sortOrder":2}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.data.sortOrder").value(2));
		long first = idOf(postJson("/api/v1/admin/notices", """
				{"title":"설 연휴 안내","content":"내용","sortOrder":1}
				"""));
		postJson("/api/v1/admin/notices", """
				{"title":"개인정보 처리방침 개정","content":"내용","pinned":true,"sortOrder":9}
				""");
		// 정렬 순서를 보내지 않으면 0번이라 고정 공지 다음 맨 앞이다.
		postJson("/api/v1/admin/notices", """
				{"title":"새 공지","content":"내용"}
				""").andExpect(jsonPath("$.data.sortOrder").value(0));

		mockMvc.perform(get("/api/v1/notices").with(anonymous()))
				.andExpect(jsonPath("$.data.content[0].title").value("개인정보 처리방침 개정"))
				.andExpect(jsonPath("$.data.content[1].title").value("새 공지"))
				.andExpect(jsonPath("$.data.content[2].title").value("설 연휴 안내"))
				.andExpect(jsonPath("$.data.content[3].title").value("배송 지연 안내"));

		patchJson("/api/v1/admin/notices/" + first, "{\"sortOrder\":5}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("설 연휴 안내"))
				.andExpect(jsonPath("$.data.sortOrder").value(5));
		mockMvc.perform(get("/api/v1/admin/notices"))
				.andExpect(jsonPath("$.data.content[3].title").value("설 연휴 안내"))
				.andExpect(jsonPath("$.data.content[3].sortOrder").value(5));
		postJson("/api/v1/admin/notices", """
				{"title":"잘못된 순서","content":"내용","sortOrder":-1}
				""").andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser
	void 일반_회원은_관리자_FAQ_목록을_볼_수_없다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/faqs")).andExpect(status().isForbidden());
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions patchJson(String path, String body) throws Exception {
		return mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}
}
