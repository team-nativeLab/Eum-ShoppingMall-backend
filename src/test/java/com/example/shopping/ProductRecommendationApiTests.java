package com.example.shopping;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 연관 상품·추천 상품·추천 검색어. 따로 표시하지 않은 테스트는 비회원으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithAnonymousUser
class ProductRecommendationApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 연관_상품은_같은_최상위_카테고리를_추천순으로_고르고_모자라면_전체_추천순으로_채운다() throws Exception {
		long protection = insertCategory("보호구", null);
		long helmets = insertCategory("안전모", protection);
		long fire = insertCategory("소방", null);
		long base = insertProduct("기준 안전모", helmets, 0, "ON_SALE");
		insertProduct("추천 안전모", helmets, 5, "ON_SALE");
		insertProduct("보안경", protection, 1, "SOLD_OUT");
		long hidden = insertProduct("숨긴 안전모", helmets, 9, "HIDDEN");
		insertProduct("소화기", fire, 3, "ON_SALE");
		insertProduct("소방 담요", fire, 0, "ON_SALE");

		mockMvc.perform(get("/api/v1/products/{id}/related", base).param("limit", "3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].name").value(contains("추천 안전모", "보안경", "소화기")));
		// 기본 10개: 기준 상품과 숨긴 상품을 뺀 전부
		mockMvc.perform(get("/api/v1/products/{id}/related", base))
				.andExpect(jsonPath("$.data[*].name").value(contains("추천 안전모", "보안경", "소화기", "소방 담요")));
		mockMvc.perform(get("/api/v1/products/{id}/related", hidden))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void 카테고리가_없는_상품의_연관_상품은_전체_추천순이다() throws Exception {
		long base = insertProduct("미분류 장비", null, 0, "ON_SALE");
		insertProduct("추천 장갑", insertCategory("장갑", null), 2, "ON_SALE");

		mockMvc.perform(get("/api/v1/products/{id}/related", base))
				.andExpect(jsonPath("$.data[*].name").value(contains("추천 장갑")));
	}

	@Test
	void 추천_상품은_고객에게_보이는_상품을_추천_점수_다음_최근_등록순으로_준다() throws Exception {
		long category = insertCategory("보호구", null);
		insertProduct("오래된 안전화", category, 0, "ON_SALE");
		insertProduct("추천 안전모", category, 5, "ON_SALE");
		insertProduct("숨긴 상품", category, 9, "HIDDEN");
		insertProduct("최신 장갑", category, 0, "SOLD_OUT");

		mockMvc.perform(get("/api/v1/products/recommended"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].name").value(contains("추천 안전모", "최신 장갑", "오래된 안전화")));
		mockMvc.perform(get("/api/v1/products/recommended").param("limit", "1"))
				.andExpect(jsonPath("$.data[*].name").value(contains("추천 안전모")));
	}

	@Test
	void 개수는_1에서_20까지다() throws Exception {
		long base = insertProduct("기준 안전모", null, 0, "ON_SALE");
		for (String limit : new String[] { "0", "21", "-1", "열" }) {
			mockMvc.perform(get("/api/v1/products/recommended").param("limit", limit))
					.andExpect(status().isBadRequest());
			mockMvc.perform(get("/api/v1/products/{id}/related", base).param("limit", limit))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(get("/api/v1/products/recommended").param("limit", "20")).andExpect(status().isOk());
	}

	@Test
	void 추천_검색어는_관리자가_정한_순서대로_보이고_공백과_중복을_정리한다() throws Exception {
		mockMvc.perform(get("/api/v1/search/recommended-keywords"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));

		saveKeywords("""
				{"keywords":[" 안전모 ","안전화","안전모","반사 안전조끼"]}
				""").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.keywords").value(contains("안전모", "안전화", "반사 안전조끼")));

		mockMvc.perform(get("/api/v1/search/recommended-keywords"))
				.andExpect(jsonPath("$.data").value(contains("안전모", "안전화", "반사 안전조끼")));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 추천_검색어는_10개까지이고_빈_검색어는_받지_않는다() throws Exception {
		saveKeywords("""
				{"keywords":["1","2","3","4","5","6","7","8","9","10","11"]}
				""").andExpect(status().isBadRequest());
		saveKeywords("""
				{"keywords":["안전모"," "]}
				""").andExpect(status().isBadRequest());
		saveKeywords("""
				{"keywords":["안전모",null]}
				""").andExpect(status().isBadRequest());
		saveKeywords("{}").andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/search/recommended-keywords"))
				.andExpect(jsonPath("$.data.keywords.length()").value(0));
	}

	@Test
	void 비회원은_추천_검색어를_바꿀_수_없다() throws Exception {
		mockMvc.perform(put("/api/v1/admin/search/recommended-keywords").contentType(MediaType.APPLICATION_JSON)
						.content("{\"keywords\":[]}"))
				.andExpect(status().isUnauthorized());
	}

	/** 관리자 권한으로 저장한다. */
	private ResultActions saveKeywords(String body) throws Exception {
		return mockMvc.perform(put("/api/v1/admin/search/recommended-keywords")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private long insertCategory(String name, Long parentId) {
		jdbcTemplate.update("insert into category (parent_id, name, sort_order, active) values (?, ?, 0, true)",
				parentId, name);
		return jdbcTemplate.queryForObject("select id from category where name = ?", Long.class, name);
	}

	private long insertProduct(String name, Long categoryId, int recommendScore, String status) {
		jdbcTemplate.update("""
				insert into product (name, category_id, brand, base_price, price_visible, recommend_score, sales_count,
				                     status, created_at, updated_at)
				values (?, ?, '세이프가드', 10000, true, ?, 0, ?, now(), now())
				""", name, categoryId, recommendScore, status);
		return jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
	}
}
