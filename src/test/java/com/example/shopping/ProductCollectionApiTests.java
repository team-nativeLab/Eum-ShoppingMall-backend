package com.example.shopping;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** 베스트·오늘의 상품 지정과 고객 상품 목록의 컬렉션 필터(#207). 관리자 API는 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class ProductCollectionApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 베스트와_오늘의_상품은_지정한_상품_가운데_고객에게_보이는_것만_나온다() throws Exception {
		long gloves = insertCategory("장갑");
		insertProduct("베스트 안전모", null, true, false, "ON_SALE");
		insertProduct("베스트 장갑", gloves, true, true, "SOLD_OUT");
		insertProduct("오늘의 안전화", null, false, true, "ON_SALE");
		insertProduct("숨긴 베스트", null, true, true, "HIDDEN");
		insertProduct("일반 상품", null, false, false, "ON_SALE");

		mockMvc.perform(get("/api/v1/products").param("collection", "featured").with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[*].name").value(containsInAnyOrder("베스트 안전모", "베스트 장갑")))
				.andExpect(jsonPath("$.data.content[0].featured").value(true));
		mockMvc.perform(get("/api/v1/products").param("collection", "today").with(anonymous()))
				.andExpect(jsonPath("$.data.content[*].name").value(containsInAnyOrder("베스트 장갑", "오늘의 안전화")));
		// 다른 조건과 함께 쓸 수 있다.
		mockMvc.perform(get("/api/v1/products").param("collection", "featured")
						.param("categoryId", String.valueOf(gloves)).with(anonymous()))
				.andExpect(jsonPath("$.data.content[*].name").value(contains("베스트 장갑")));
		// 지정하지 않으면 전체(숨긴 상품 제외)
		mockMvc.perform(get("/api/v1/products").with(anonymous()))
				.andExpect(jsonPath("$.data.totalElements").value(4));
	}

	@Test
	void 없는_컬렉션_값은_400이다() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("collection", "sale").with(anonymous()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 관리자가_상품을_베스트와_오늘의_상품으로_지정하고_보낸_항목만_바꾼다() throws Exception {
		String created = mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"ABS 안전모","basePrice":30000,"featured":true,
								 "skus":[{"optionName":"흰색","price":30000,"stock":10}]}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.featured").value(true))
				.andExpect(jsonPath("$.data.todayDeal").value(false))
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.data.id")).longValue();

		mockMvc.perform(patch("/api/v1/admin/products/{id}", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"todayDeal\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.featured").value(true))
				.andExpect(jsonPath("$.data.todayDeal").value(true));
		mockMvc.perform(patch("/api/v1/admin/products/{id}", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"featured\":false}"))
				.andExpect(jsonPath("$.data.featured").value(false))
				.andExpect(jsonPath("$.data.todayDeal").value(true));

		mockMvc.perform(get("/api/v1/products/{id}", id).with(anonymous()))
				.andExpect(jsonPath("$.data.featured").value(false))
				.andExpect(jsonPath("$.data.todayDeal").value(true));
		mockMvc.perform(get("/api/v1/admin/products").param("keyword", "ABS"))
				.andExpect(jsonPath("$.data.content[0].todayDeal").value(true));
	}

	private long insertCategory(String name) {
		jdbcTemplate.update("insert into category (parent_id, name, sort_order, active) values (null, ?, 0, true)",
				name);
		return jdbcTemplate.queryForObject("select id from category where name = ?", Long.class, name);
	}

	private void insertProduct(String name, Long categoryId, boolean featured, boolean todayDeal, String status) {
		jdbcTemplate.update("""
				insert into product (name, category_id, brand, base_price, price_visible, featured, today_deal, status,
				                     created_at, updated_at)
				values (?, ?, '세이프가드', 10000, true, ?, ?, ?, now(), now())
				""", name, categoryId, featured, todayDeal, status);
	}
}
