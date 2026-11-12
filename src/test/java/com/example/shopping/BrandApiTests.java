package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.example.shopping.domain.product.repository.BrandRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** 브랜드 목록·상세·대표 브랜드와 관리자 브랜드 관리. 따로 표시하지 않은 테스트는 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class BrandApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private BrandRepository brandRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 보이는_브랜드만_정렬_순서대로_보여_주고_고객에게_보이는_상품_수를_센다() throws Exception {
		create(brand("세이프가드", true, false, 2)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.visible").value(true));
		create(brand("워크핏", true, false, 1));
		long hiddenId = idOf(create(brand("숨긴 브랜드", false, false, 0)));
		insertProduct("ABS 안전모", "세이프가드", "ON_SALE");
		insertProduct("통풍형 안전모", "세이프가드", "SOLD_OUT");
		insertProduct("숨긴 안전모", "세이프가드", "HIDDEN");

		mockMvc.perform(get("/api/v1/brands").with(request -> request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].name").value("워크핏"))
				.andExpect(jsonPath("$.data[0].productCount").value(0))
				.andExpect(jsonPath("$.data[1].name").value("세이프가드"))
				.andExpect(jsonPath("$.data[1].productCount").value(2))
				.andExpect(jsonPath("$.data[1].tagline").value("현장의 안전을 지킵니다"));
		mockMvc.perform(get("/api/v1/brands/{id}", hiddenId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));
		// 관리자 목록에는 숨긴 브랜드도 있다.
		mockMvc.perform(get("/api/v1/admin/brands"))
				.andExpect(jsonPath("$.data.length()").value(3));
	}

	@Test
	@WithAnonymousUser
	void 비회원도_브랜드_상세와_대표_브랜드를_본다() throws Exception {
		jdbcTemplate.update("""
				insert into brand (name, english_name, tagline, main_field, origin_country, certification, story_title,
				                   story_body, visible, featured, sort_order, created_at, updated_at)
				values ('하이세이프', 'HighSafe', '추락 방지 전문', '추락 방지', '대한민국', 'KCs 인증', '우리 이야기',
				        '1998년 시작', true, true, 0, now(), now())
				""");
		Long id = jdbcTemplate.queryForObject("select id from brand where name = '하이세이프'", Long.class);

		mockMvc.perform(get("/api/v1/brands/featured"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(id))
				.andExpect(jsonPath("$.data.story.title").value("우리 이야기"));
		mockMvc.perform(get("/api/v1/brands/{id}", id))
				.andExpect(jsonPath("$.data.englishName").value("HighSafe"))
				.andExpect(jsonPath("$.data.mainField").value("추락 방지"))
				.andExpect(jsonPath("$.data.originCountry").value("대한민국"));
		mockMvc.perform(post("/api/v1/admin/brands").contentType(MediaType.APPLICATION_JSON)
						.content(brand("무단", true, false, 0)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 이름을_바꾸면_그_이름을_쓰던_상품의_브랜드도_바뀌고_같은_이름은_막는다() throws Exception {
		long brandId = idOf(create(brand("세이프가드", true, false, 0)));
		create(brand("워크핏", true, false, 0));
		long productId = insertProduct("ABS 안전모", "세이프가드", "ON_SALE");

		mockMvc.perform(put("/api/v1/admin/brands/{id}", brandId).contentType(MediaType.APPLICATION_JSON)
						.content(brand("SafeGuard", true, true, 0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("SafeGuard"))
				.andExpect(jsonPath("$.data.featured").value(true))
				.andExpect(jsonPath("$.data.productCount").value(1));
		entityManager.clear();
		assertThat(jdbcTemplate.queryForObject("select brand from product where id = ?", String.class, productId))
				.isEqualTo("SafeGuard");

		mockMvc.perform(put("/api/v1/admin/brands/{id}", brandId).contentType(MediaType.APPLICATION_JSON)
						.content(brand("워크핏", true, false, 0)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BRAND_NAME_DUPLICATED"));
		create(brand("SafeGuard", true, false, 0)).andExpect(status().isConflict());

		// 브랜드 소개를 지워도 상품의 브랜드 값은 그대로다.
		mockMvc.perform(delete("/api/v1/admin/brands/{id}", brandId)).andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select brand from product where id = ?", String.class, productId))
				.isEqualTo("SafeGuard");
	}

	@Test
	void 이미지는_업로드한_주소만_받고_참조_없는_이미지_정리가_지우지_않게_알린다() throws Exception {
		create("""
				{"name":"외부 로고","logoUrl":"https://evil.example/logo.png"}
				""").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_IMAGE_URL"));
		create("""
				{"name":"사인랩","logoUrl":"/files/brand-logo.png","coverImageUrl":"/files/brand-cover.png"}
				""").andExpect(status().isCreated());

		entityManager.flush();
		assertThat(brandRepository.findAllImageUrls())
				.contains("/files/brand-logo.png", "/files/brand-cover.png");
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/brands").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String brand(String name, boolean visible, boolean featured, int sortOrder) {
		return """
				{"name":"%s","tagline":"현장의 안전을 지킵니다","visible":%s,"featured":%s,"sortOrder":%d}
				""".formatted(name, visible, featured, sortOrder);
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}

	private long insertProduct(String name, String brand, String status) {
		jdbcTemplate.update("""
				insert into product (name, brand, base_price, price_visible, status, created_at, updated_at)
				values (?, ?, 10000, true, ?, now(), now())
				""", name, brand, status);
		return jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
	}
}
