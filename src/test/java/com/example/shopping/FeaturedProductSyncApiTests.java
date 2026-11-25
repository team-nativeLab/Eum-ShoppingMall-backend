package com.example.shopping;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** 상품의 베스트 지정과 홈 편성의 베스트 목록 연동(#219). 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class FeaturedProductSyncApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 홈_편성의_베스트_목록이_상품의_베스트_지정이_된다() throws Exception {
		// #219 전에 상품에서만 베스트로 지정한 상품
		insertProduct("ABS 안전모", true);
		long gloves = insertProduct("코팅 장갑", false);
		long boots = insertProduct("경량 안전화", false);

		saveHome(List.of(gloves, boots)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/products").param("collection", "featured").with(anonymous()))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[*].name").value(containsInAnyOrder("코팅 장갑", "경량 안전화")));
		mockMvc.perform(get("/api/v1/admin/products/{id}", gloves))
				.andExpect(jsonPath("$.data.featured").value(true));

		saveHome(List.of()).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/products").param("collection", "featured").with(anonymous()))
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void 상품에서_베스트를_켜면_홈_베스트_목록_끝에_들어가고_끄거나_지우면_빠진다() throws Exception {
		long gloves = insertProduct("코팅 장갑", false);
		saveHome(List.of(gloves)).andExpect(status().isOk());

		long helmet = idOf(mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"ABS 안전모","basePrice":30000,"featured":true,
								 "skus":[{"optionName":"흰색","price":30000,"stock":10}]}
								"""))
				.andExpect(status().isCreated()));
		featuredIds()
				.andExpect(jsonPath("$.data.featuredProductIds.length()").value(2))
				.andExpect(jsonPath("$.data.featuredProductIds[0]").value(gloves))
				.andExpect(jsonPath("$.data.featuredProductIds[1]").value(helmet));

		// 이미 베스트인 상품을 다시 베스트로 보내도 그대로다.
		patchFeatured(gloves, true).andExpect(status().isOk());
		patchFeatured(helmet, false).andExpect(status().isOk());
		featuredIds()
				.andExpect(jsonPath("$.data.featuredProductIds.length()").value(1))
				.andExpect(jsonPath("$.data.featuredProductIds[0]").value(gloves));

		// 지운 상품은 홈 베스트 목록에서 빠져, 홈 편성을 다시 저장해도 막히지 않는다.
		mockMvc.perform(delete("/api/v1/admin/products/{id}", gloves)).andExpect(status().isOk());
		featuredIds().andExpect(jsonPath("$.data.featuredProductIds.length()").value(0));
		saveHome(List.of()).andExpect(status().isOk());
	}

	@Test
	void 홈_베스트_목록이_꽉_차면_상품에서_베스트를_켤_수_없다() throws Exception {
		List<Long> full = LongStream.rangeClosed(900_001, 900_100).boxed().toList();
		jdbcTemplate.update("""
				insert into site_setting (setting_key, value_json, created_at, updated_at)
				values ('home-layout', ?, now(), now())
				""", layout(full));
		long gloves = insertProduct("코팅 장갑", false);

		patchFeatured(gloves, true)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("HOME_FEATURED_PRODUCTS_FULL"));
		featuredIds().andExpect(jsonPath("$.data.featuredProductIds.length()").value(100));
		// 홈 편성에서도 100개를 넘겨 저장할 수 없다.
		saveHome(LongStream.rangeClosed(1, 101).boxed().toList())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("featuredProductIds"));
	}

	private ResultActions saveHome(List<Long> featuredProductIds) throws Exception {
		return mockMvc.perform(put("/api/v1/admin/home").contentType(MediaType.APPLICATION_JSON)
				.content(layout(featuredProductIds)));
	}

	private ResultActions featuredIds() throws Exception {
		return mockMvc.perform(get("/api/v1/admin/home")).andExpect(status().isOk());
	}

	private ResultActions patchFeatured(long productId, boolean featured) throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/products/{id}", productId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"featured\":" + featured + "}"));
	}

	private static String layout(List<Long> featuredProductIds) {
		return """
				{"heroSlides":[],"promoBanners":[],"serviceBenefits":[],"featuredProductIds":[%s]}
				""".formatted(featuredProductIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
	}

	private long insertProduct(String name, boolean featured) {
		jdbcTemplate.update("""
				insert into product (name, brand, base_price, price_visible, featured, status, created_at, updated_at)
				values (?, '세이프가드', 10000, true, ?, 'ON_SALE', now(), now())
				""", name, featured);
		long productId = jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
		// 판매중 상품은 옵션이 하나 이상 있어야 수정할 수 있다.
		jdbcTemplate.update("""
				insert into product_sku (product_id, option_name, price, stock, version) values (?, '기본', 10000, 10, 0)
				""", productId);
		return productId;
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}
}
