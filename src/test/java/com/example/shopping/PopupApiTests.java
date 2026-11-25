package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.example.shopping.domain.home.repository.PopupRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** 웹 홈 팝업(#211). 따로 표시하지 않은 테스트는 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class PopupApiTests {

	private static final String POPUP = """
			{"title":" 현장 안전용품 기획전 ","imageUrl":" /files/popup.png ","body":"이번 주 안전모 10% 할인",
			 "visible":true,"sortOrder":1}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PopupRepository popupRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 웹_홈은_노출_중인_팝업만_정렬_순서대로_받는다() throws Exception {
		long sale = idOf(create(POPUP)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.title").value("현장 안전용품 기획전"))
				.andExpect(jsonPath("$.data.imageUrl").value("/files/popup.png"))
				.andExpect(jsonPath("$.data.visible").value(true)));
		// 사진 없이 정렬 순서를 비워 보내면 사진 없음, 0번이다.
		long notice = idOf(create("""
				{"title":"배송 안내","imageUrl":"","body":"설 연휴 배송이 늦어집니다."}
				""")
				.andExpect(jsonPath("$.data.imageUrl").doesNotExist())
				.andExpect(jsonPath("$.data.sortOrder").value(0)));
		long hidden = idOf(create(POPUP.replace("현장 안전용품 기획전", "준비 중인 팝업")
				.replace("\"visible\":true", "\"visible\":false").replace("\"sortOrder\":1", "\"sortOrder\":0")));

		mockMvc.perform(get("/api/v1/popups").with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].id").value(notice))
				.andExpect(jsonPath("$.data[0].body").value("설 연휴 배송이 늦어집니다."))
				.andExpect(jsonPath("$.data[1].id").value(sale))
				.andExpect(jsonPath("$.data[1].visible").doesNotExist())
				.andExpect(jsonPath("$.data[1].sortOrder").doesNotExist());
		// 관리자는 숨긴 팝업도 본다. 정렬 순서가 같으면 최근 등록한 팝업이 앞이다.
		mockMvc.perform(get("/api/v1/admin/popups"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(3))
				.andExpect(jsonPath("$.data[0].id").value(hidden))
				.andExpect(jsonPath("$.data[0].visible").value(false))
				.andExpect(jsonPath("$.data[1].id").value(notice))
				.andExpect(jsonPath("$.data[2].id").value(sale));

		mockMvc.perform(put("/api/v1/admin/popups/{id}", sale).contentType(MediaType.APPLICATION_JSON)
						.content(POPUP.replace("\"visible\":true", "\"visible\":false")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.visible").value(false));
		mockMvc.perform(delete("/api/v1/admin/popups/{id}", notice)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/popups").with(anonymous()))
				.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(delete("/api/v1/admin/popups/{id}", notice))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("POPUP_NOT_FOUND"));
	}

	@Test
	void 제목_내용이_없거나_정렬_순서와_사진_주소가_맞지_않으면_거절한다() throws Exception {
		create(POPUP.replace(" 현장 안전용품 기획전 ", " ")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		create(POPUP.replace("이번 주 안전모 10% 할인", "")).andExpect(status().isBadRequest());
		create(POPUP.replace("\"sortOrder\":1", "\"sortOrder\":-1")).andExpect(status().isBadRequest());
		// 외부 주소와 data URL은 받지 않는다. 사진 파일은 업로드 API로 올린 뒤 그 주소를 보낸다.
		for (String imageUrl : List.of("https://evil.example/popup.png", "data:image/png;base64,iVBORw0KGgo=",
				"/assets/../files/x.png", "javascript:alert(1)")) {
			create(POPUP.replace(" /files/popup.png ", imageUrl))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_HOME_IMAGE_URL"));
		}
		create(POPUP.replace(" /files/popup.png ", "/assets/web/popup-safety.webp")).andExpect(status().isCreated());
		mockMvc.perform(put("/api/v1/admin/popups/{id}", 999_999).contentType(MediaType.APPLICATION_JSON)
						.content(POPUP))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("POPUP_NOT_FOUND"));
	}

	@Test
	void 팝업_사진은_참조_없는_이미지_정리가_지우지_않게_알린다() throws Exception {
		create(POPUP).andExpect(status().isCreated());
		create(POPUP.replace(" /files/popup.png ", "")).andExpect(status().isCreated());
		entityManager.flush();

		assertThat(popupRepository.findAllImageUrls()).containsExactly("/files/popup.png");
	}

	@Test
	@WithAnonymousUser
	void 비회원은_팝업을_바꿀_수_없다() throws Exception {
		create(POPUP).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/popups")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void 일반_회원은_팝업을_바꿀_수_없다() throws Exception {
		create(POPUP).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/popups")).andExpect(status().isForbidden());
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/popups").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}
}
