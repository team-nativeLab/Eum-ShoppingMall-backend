package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.event.entity.ShopEvent;
import com.example.shopping.domain.event.entity.ShopEventStatus;
import com.jayway.jsonpath.JsonPath;

/** 이벤트·기획전 목록·상세와 관리자 이벤트 관리. 따로 표시하지 않은 테스트는 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class ShopEventApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 보이는_이벤트만_정렬_순서대로_보여_주고_진행_상태는_기간으로_정한다() throws Exception {
		OffsetDateTime now = OffsetDateTime.now();
		long ongoing = idOf(create(event("안전 준비 기획전", now.minusDays(1), now.plusDays(1), true, 1)));
		create(event("겨울 대비 기획전", now.plusDays(2), now.plusDays(5), true, 0));
		create(event("지난 기획전", now.minusDays(5), now.minusDays(2), true, 2));
		long hidden = idOf(create(event("숨긴 기획전", now.minusDays(1), now.plusDays(1), false, 0)));

		mockMvc.perform(get("/api/v1/events").with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].title").value(contains("겨울 대비 기획전", "안전 준비 기획전", "지난 기획전")))
				.andExpect(jsonPath("$.data[*].status").value(contains("UPCOMING", "ONGOING", "ENDED")));
		mockMvc.perform(get("/api/v1/events/{id}", ongoing).with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.heroTitle").value(contains("현장을 위한", "안전 준비")))
				.andExpect(jsonPath("$.data.sections[1].heading").value("유의사항"))
				.andExpect(jsonPath("$.data.searchKeyword").value("안전"))
				.andExpect(jsonPath("$.data.visible").doesNotExist());
		mockMvc.perform(get("/api/v1/events/{id}", hidden).with(anonymous()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		// 관리자 목록에는 숨긴 이벤트도 있다.
		mockMvc.perform(get("/api/v1/admin/events"))
				.andExpect(jsonPath("$.data.length()").value(4))
				.andExpect(jsonPath("$.data[?(@.visible == false)].title").value(contains("숨긴 기획전")));
	}

	@Test
	void 시각은_서버_시간대로_저장하고_오프셋을_붙여_돌려준다() throws Exception {
		create(event("시간대 확인", OffsetDateTime.parse("2026-10-01T00:00:00Z"),
				OffsetDateTime.parse("2026-10-31T14:59:59Z"), true, 0))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.startAt").value("2026-10-01T09:00:00+09:00"))
				.andExpect(jsonPath("$.data.endAt").value("2026-10-31T23:59:59+09:00"));
	}

	@Test
	void 이벤트를_수정하면_본문_구성도_통째로_바뀌고_지우면_사라진다() throws Exception {
		OffsetDateTime now = OffsetDateTime.now();
		long id = idOf(create(event("안전 준비 기획전", now.minusDays(1), now.plusDays(1), true, 0)));

		mockMvc.perform(put("/api/v1/admin/events/{id}", id).contentType(MediaType.APPLICATION_JSON)
						.content(event("이름 바꾼 기획전", now.minusDays(1), now.plusDays(1), true, 0)
								.replace(",{\"heading\":\"유의사항\",\"body\":\"재고는 상세 페이지에서 확인해 주세요.\"}", "")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("이름 바꾼 기획전"))
				.andExpect(jsonPath("$.data.sections.length()").value(1));

		mockMvc.perform(delete("/api/v1/admin/events/{id}", id)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/events/{id}", id)).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/admin/events/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	@Test
	void 기간이_거꾸로이거나_형식이_틀린_이벤트는_거절한다() throws Exception {
		OffsetDateTime now = OffsetDateTime.now();
		String valid = event("안전 준비 기획전", now, now.plusDays(1), true, 0);

		create(event("거꾸로 된 기간", now, now.minusSeconds(1), true, 0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_EVENT_PERIOD"));
		List<String> invalid = List.of(
				valid.replace("[\"현장을 위한\",\"안전 준비\"]", "[]"),
				valid.replace("\"현장을 위한\"", "\"현장을\\n위한\""),
				valid.replace("\"title\":\"안전 준비 기획전\"", "\"title\":\" \""),
				valid.replace("\"startAt\":\"" + now + "\"", "\"startAt\":\"2026-10-01T00:00:00\""),
				valid.replace("\"sortOrder\":0", "\"sortOrder\":-1"),
				valid.replace("\"sections\":[", "\"sections\":[" + "{\"heading\":\"구성\",\"body\":\"내용\"},".repeat(9)));
		for (String body : invalid) {
			create(body).andExpect(status().isBadRequest());
		}
	}

	@Test
	void 종료_시각과_같은_시각까지는_진행_중이다() {
		LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
		LocalDateTime end = LocalDateTime.of(2026, 10, 31, 23, 59, 59);
		ShopEvent event = ShopEvent.create(new ShopEvent.Fields("기획전", "", "", List.of("제목"), "", "", List.of(),
				"", start, end, true, 0));

		assertThat(event.statusAt(start.minusSeconds(1))).isEqualTo(ShopEventStatus.UPCOMING);
		assertThat(event.statusAt(start)).isEqualTo(ShopEventStatus.ONGOING);
		assertThat(event.statusAt(end)).isEqualTo(ShopEventStatus.ONGOING);
		assertThat(event.statusAt(end.plusNanos(1))).isEqualTo(ShopEventStatus.ENDED);
	}

	@Test
	@WithAnonymousUser
	void 비회원은_이벤트를_등록할_수_없다() throws Exception {
		OffsetDateTime now = OffsetDateTime.now();
		create(event("무단 등록", now, now.plusDays(1), true, 0)).andExpect(status().isUnauthorized());
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/events").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String event(String title, OffsetDateTime startAt, OffsetDateTime endAt, boolean visible,
			int sortOrder) {
		return """
				{"title":"%s","subtitle":"현장 필수 안전용품을 한곳에","eyebrow":"EUM SAFETY WEEK",\
				"heroTitle":["현장을 위한","안전 준비"],"heroDescription":"필요한 안전용품을 둘러보세요.",\
				"body":"현장 운영에 필요한 안전용품을 모아 소개합니다.",\
				"sections":[{"heading":"기획전 구성","body":"개인보호장비 · 작업복"},\
				{"heading":"유의사항","body":"재고는 상세 페이지에서 확인해 주세요."}],\
				"searchKeyword":"안전","startAt":"%s","endAt":"%s","visible":%s,"sortOrder":%d}
				""".formatted(title, startAt, endAt, visible, sortOrder);
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}
}
