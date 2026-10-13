package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.example.shopping.domain.product.repository.CategoryRepository;
import com.example.shopping.domain.product.service.DefaultCategoryInitializer;
import com.jayway.jsonpath.JsonPath;

/** 고객용 상품 목록·검색: 필터(카테고리, 브랜드, 가격대, 안전 인증), 정렬, 페이지 크기 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductSearchTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CategoryRepository categoryRepository;

	// ───────── 페이지 ─────────

	@Test
	void 기본은_추천순으로_9개씩_나오고_크기를_바꿀_수_있다() throws Exception {
		for (int score = 1; score <= 10; score++) {
			insert(product("상품 " + score).recommendScore(score));
		}

		mockMvc.perform(get("/api/v1/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.size").value(9))
				.andExpect(jsonPath("$.data.content.length()").value(9))
				.andExpect(jsonPath("$.data.totalElements").value(10))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content[0].name").value("상품 10"))
				.andExpect(jsonPath("$.data.content[8].name").value("상품 2"));
		mockMvc.perform(get("/api/v1/products").param("page", "1"))
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].name").value("상품 1"));
		mockMvc.perform(get("/api/v1/products").param("size", "5"))
				.andExpect(jsonPath("$.data.size").value(5))
				.andExpect(jsonPath("$.data.content.length()").value(5))
				.andExpect(jsonPath("$.data.totalPages").value(2));
	}

	// ───────── 필터 ─────────

	@Test
	void 카테고리와_브랜드와_안전_인증_여부로_걸러_볼_수_있다() throws Exception {
		long helmets = insertCategory("안전모");
		long goggles = insertCategory("보안경");
		insert(product("경량 안전모").category(helmets).brand("3M").certified(true));
		insert(product("일반 안전모").category(helmets).brand("허니웰"));
		insert(product("김서림 방지 보안경").category(goggles).brand("3M").certified(true));
		insert(product("브랜드 없는 보안경").category(goggles));
		insert(product("비공개 안전모").category(helmets).brand("3M").certified(true).status("HIDDEN"));

		mockMvc.perform(get("/api/v1/products"))
				.andExpect(jsonPath("$.data.totalElements").value(4));
		mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(helmets)))
				.andExpect(jsonPath("$.data.content[*].name").value(containsInAnyOrder("경량 안전모", "일반 안전모")));
		mockMvc.perform(get("/api/v1/products").param("brand", "3M"))
				.andExpect(jsonPath("$.data.content[*].name")
						.value(containsInAnyOrder("경량 안전모", "김서림 방지 보안경")));
		mockMvc.perform(get("/api/v1/products").param("brand", "3M").param("brand", "허니웰"))
				.andExpect(jsonPath("$.data.totalElements").value(3));
		mockMvc.perform(get("/api/v1/products").param("certified", "true"))
				.andExpect(jsonPath("$.data.content[*].name")
						.value(containsInAnyOrder("경량 안전모", "김서림 방지 보안경")))
				.andExpect(jsonPath("$.data.content[0].safetyCertified").value(true));

		// 조건을 함께 주면 모두 만족하는 상품만 나온다.
		mockMvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(goggles))
						.param("brand", "3M").param("certified", "true"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].name").value("김서림 방지 보안경"))
				.andExpect(jsonPath("$.data.content[0].brand").value("3M"));
	}

	@Test
	void 검색어는_상품명과_브랜드에서_찾고_특수문자는_글자_그대로_찾는다() throws Exception {
		insert(product("경량 안전모").brand("허니웰"));
		insert(product("100% 면장갑").brand("3M"));
		insert(product("100 면장갑").brand("3M"));

		mockMvc.perform(get("/api/v1/products").param("query", "안전모"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/v1/products").param("query", "허니"))
				.andExpect(jsonPath("$.data.content[0].name").value("경량 안전모"));
		mockMvc.perform(get("/api/v1/products").param("query", "3m"))
				.andExpect(jsonPath("$.data.totalElements").value(2));
		// %가 "아무 글자"로 해석되면 두 상품이 모두 나온다.
		mockMvc.perform(get("/api/v1/products").param("query", "100%"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].name").value("100% 면장갑"));
		mockMvc.perform(get("/api/v1/products").param("query", "_"))
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void 가격대로_걸러_볼_수_있고_금액_미표시_상품은_가격대_검색에_나오지_않는다() throws Exception {
		insert(product("1만원 상품").price(10000));
		insert(product("2만원 상품").price(20000));
		insert(product("3만원 상품").price(30000));
		insert(product("가격 비공개 상품").price(15000).priceVisible(false));

		mockMvc.perform(get("/api/v1/products"))
				.andExpect(jsonPath("$.data.totalElements").value(4));
		// 경계값을 포함한다.
		mockMvc.perform(get("/api/v1/products").param("minPrice", "10000").param("maxPrice", "20000"))
				.andExpect(jsonPath("$.data.content[*].name").value(containsInAnyOrder("1만원 상품", "2만원 상품")));
		mockMvc.perform(get("/api/v1/products").param("minPrice", "15000"))
				.andExpect(jsonPath("$.data.content[*].name").value(containsInAnyOrder("2만원 상품", "3만원 상품")));
		mockMvc.perform(get("/api/v1/products").param("maxPrice", "15000"))
				.andExpect(jsonPath("$.data.content[*].name").value(contains("1만원 상품")));

		// 범위를 좁혀 가며 숨긴 가격(15000원)을 알아내려 해도 결과에 나오지 않는다.
		mockMvc.perform(get("/api/v1/products").param("minPrice", "15000").param("maxPrice", "15000"))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/v1/products").param("minPrice", "0").param("maxPrice", "99999999"))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(content().string(not(containsString("가격 비공개 상품"))));
	}

	@Test
	void 잘못된_검색_조건은_400으로_거절한다() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("minPrice", "-1")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/products").param("minPrice", "30000").param("maxPrice", "10000"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/products").param("minPrice", "만원")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/products").param("certified", "아마도")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/products").param("sort", "cheapest"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	// ───────── 정렬 ─────────

	@Test
	void 추천순_인기순_가격순으로_정렬할_수_있다() throws Exception {
		insert(product("추천 1위").recommendScore(100).salesCount(1).price(30000));
		insert(product("판매 1위").recommendScore(10).salesCount(500).price(20000));
		insert(product("최저가").recommendScore(50).salesCount(30).price(10000));

		mockMvc.perform(get("/api/v1/products").param("sort", "recommended"))
				.andExpect(jsonPath("$.data.content[*].name").value(contains("추천 1위", "최저가", "판매 1위")));
		mockMvc.perform(get("/api/v1/products").param("sort", "popular"))
				.andExpect(jsonPath("$.data.content[*].name").value(contains("판매 1위", "최저가", "추천 1위")));
		mockMvc.perform(get("/api/v1/products").param("sort", "price_asc"))
				.andExpect(jsonPath("$.data.content[*].name").value(contains("최저가", "판매 1위", "추천 1위")));
		mockMvc.perform(get("/api/v1/products").param("sort", "price_desc"))
				.andExpect(jsonPath("$.data.content[*].name").value(contains("추천 1위", "판매 1위", "최저가")));
		// 대소문자는 가리지 않는다.
		mockMvc.perform(get("/api/v1/products").param("sort", "POPULAR"))
				.andExpect(jsonPath("$.data.content[0].name").value("판매 1위"));
	}

	@Test
	void 정렬과_필터와_페이지를_함께_쓸_수_있다() throws Exception {
		insert(product("A").brand("3M").price(30000));
		insert(product("B").brand("3M").price(10000));
		insert(product("C").brand("3M").price(20000));
		insert(product("D").brand("허니웰").price(5000));

		mockMvc.perform(get("/api/v1/products").param("brand", "3M").param("sort", "price_asc")
						.param("size", "2"))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.content[*].name").value(contains("B", "C")));
		mockMvc.perform(get("/api/v1/products").param("brand", "3M").param("sort", "price_asc")
						.param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.data.content[*].name").value(contains("A")));
	}

	// ───────── 브랜드 목록 ─────────

	@Test
	void 브랜드_목록은_고객에게_보이는_상품의_브랜드만_중복_없이_내려준다() throws Exception {
		long helmets = insertCategory("안전모");
		insert(product("안전모 1").category(helmets).brand("허니웰"));
		insert(product("안전모 2").category(helmets).brand("3M"));
		insert(product("안전모 3").category(helmets).brand("3M"));
		insert(product("장갑").brand("코오롱"));
		insert(product("브랜드 없음"));
		insert(product("비공개").brand("숨김브랜드").status("HIDDEN"));
		insert(product("삭제됨").brand("삭제브랜드").status("DELETED"));

		mockMvc.perform(get("/api/v1/products/brands"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(contains("3M", "코오롱", "허니웰")));
		mockMvc.perform(get("/api/v1/products/brands").param("categoryId", String.valueOf(helmets)))
				.andExpect(jsonPath("$.data").value(contains("3M", "허니웰")));
	}

	// ───────── 관리자 입력 ─────────

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자가_브랜드와_안전_인증과_추천_점수를_입력하면_검색에_반영된다() throws Exception {
		String created = mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"경량 안전모","basePrice":30000,"brand":"3M","safetyCertified":true,"recommendScore":70}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.brand").value("3M"))
				.andExpect(jsonPath("$.data.safetyCertified").value(true))
				.andExpect(jsonPath("$.data.recommendScore").value(70))
				.andExpect(jsonPath("$.data.salesCount").value(0))
				.andReturn().getResponse().getContentAsString();
		long productId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

		mockMvc.perform(get("/api/v1/products").param("brand", "3M").param("certified", "true"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(jsonPath("$.data.brand").value("3M"))
				.andExpect(jsonPath("$.data.safetyCertified").value(true));

		// 보낸 항목만 바뀌고, 브랜드는 빈 문자열을 보내면 지워진다.
		mockMvc.perform(patch("/api/v1/admin/products/{id}", productId).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"safetyCertified":false}
								"""))
				.andExpect(jsonPath("$.data.brand").value("3M"))
				.andExpect(jsonPath("$.data.safetyCertified").value(false))
				.andExpect(jsonPath("$.data.recommendScore").value(70));
		mockMvc.perform(get("/api/v1/products").param("certified", "true"))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(patch("/api/v1/admin/products/{id}", productId).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"brand":""}
								"""))
				.andExpect(jsonPath("$.data.brand").doesNotExist());
		mockMvc.perform(get("/api/v1/products/brands")).andExpect(jsonPath("$.data.length()").value(0));

		mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"안전모","basePrice":30000,"recommendScore":-1}
								"""))
				.andExpect(status().isBadRequest());
	}

	// ───────── 기본 카테고리 ─────────

	@Test
	void 카테고리가_하나도_없으면_기본_카테고리_6개를_만들고_이미_있으면_건드리지_않는다() throws Exception {
		DefaultCategoryInitializer initializer = new DefaultCategoryInitializer(categoryRepository);

		initializer.run(null);
		initializer.run(null);

		mockMvc.perform(get("/api/v1/categories"))
				.andExpect(jsonPath("$.data[*].name")
						.value(contains("안전모", "보안경", "안전장갑", "방진마스크", "안전화", "귀마개")));
		assertThat(jdbcTemplate.queryForObject("select count(*) from category", Long.class)).isEqualTo(6);
	}

	@Test
	void 관리자가_만든_카테고리가_있으면_기본_카테고리를_추가하지_않는다() {
		insertCategory("직접 만든 카테고리");

		new DefaultCategoryInitializer(categoryRepository).run(null);

		assertThat(jdbcTemplate.queryForObject("select count(*) from category", Long.class)).isEqualTo(1);
	}

	// ───────── 도우미 ─────────

	private long insertCategory(String name) {
		jdbcTemplate.update("insert into category (name, sort_order, active) values (?, 0, true)", name);
		return jdbcTemplate.queryForObject("select id from category where name = ?", Long.class, name);
	}

	private ProductRow product(String name) {
		return new ProductRow(name);
	}

	private void insert(ProductRow row) {
		jdbcTemplate.update("""
				insert into product (name, category_id, brand, base_price, price_visible, safety_certified,
				                     recommend_score, sales_count, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
				""", row.name, row.categoryId, row.brand, row.price, row.priceVisible, row.certified,
				row.recommendScore, row.salesCount, row.status);
	}

	/** 테스트용 상품 한 줄. 지정하지 않은 값은 가장 평범한 값(1만원, 가격 공개, 인증 없음, 판매중)이다. */
	private static class ProductRow {
		private final String name;
		private Long categoryId;
		private String brand;
		private long price = 10000;
		private boolean priceVisible = true;
		private boolean certified;
		private int recommendScore;
		private long salesCount;
		private String status = "ON_SALE";

		ProductRow(String name) {
			this.name = name;
		}

		ProductRow category(long categoryId) {
			this.categoryId = categoryId;
			return this;
		}

		ProductRow brand(String brand) {
			this.brand = brand;
			return this;
		}

		ProductRow price(long price) {
			this.price = price;
			return this;
		}

		ProductRow priceVisible(boolean priceVisible) {
			this.priceVisible = priceVisible;
			return this;
		}

		ProductRow certified(boolean certified) {
			this.certified = certified;
			return this;
		}

		ProductRow recommendScore(int recommendScore) {
			this.recommendScore = recommendScore;
			return this;
		}

		ProductRow salesCount(long salesCount) {
			this.salesCount = salesCount;
			return this;
		}

		ProductRow status(String status) {
			this.status = status;
			return this;
		}
	}
}
