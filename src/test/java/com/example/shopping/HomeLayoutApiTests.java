package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;

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

import com.example.shopping.domain.home.service.HomeLayoutImageReferences;

import jakarta.persistence.EntityManager;

/** 관리자 홈 편성과 고객 홈. 따로 표시하지 않은 테스트는 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class HomeLayoutApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private HomeLayoutImageReferences homeLayoutImageReferences;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 저장한_홈_편성을_고객_홈이_보여_주고_베스트_상품은_고객에게_보이는_상품만_고른_순서대로_준다() throws Exception {
		long helmet = insertProduct("ABS 안전모", "ON_SALE");
		long hidden = insertProduct("숨긴 안전화", "HIDDEN");
		long gloves = insertProduct("코팅 장갑", "SOLD_OUT");

		save(layout(List.of(gloves, hidden, helmet, gloves)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.featuredProductIds.length()").value(3))
				.andExpect(jsonPath("$.data.featuredProductIds[0]").value(gloves))
				.andExpect(jsonPath("$.data.featuredProductIds[2]").value(helmet));

		mockMvc.perform(get("/api/v1/home").with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.heroSlides.length()").value(1))
				.andExpect(jsonPath("$.data.heroSlides[0].title[1]").value("더 안전한 내일을."))
				.andExpect(jsonPath("$.data.heroSlides[0].tagline[0]").value("안전이"))
				.andExpect(jsonPath("$.data.heroSlides[0].secondaryCta.destination").value("/about"))
				.andExpect(jsonPath("$.data.promoBanners[0].tone").value("dark"))
				.andExpect(jsonPath("$.data.promoBanners[0].destination").value("/search?keyword=안전모"))
				.andExpect(jsonPath("$.data.serviceBenefits.length()").value(2))
				.andExpect(jsonPath("$.data.serviceBenefits[1].description").value(""))
				.andExpect(jsonPath("$.data.featuredProducts.length()").value(2))
				.andExpect(jsonPath("$.data.featuredProducts[0].name").value("코팅 장갑"))
				.andExpect(jsonPath("$.data.featuredProducts[1].name").value("ABS 안전모"))
				.andExpect(jsonPath("$.data.ctaBand.cta.destination").value("/quotes/new"));
		// 관리자 화면에는 숨긴 상품도 저장한 그대로 보인다.
		mockMvc.perform(get("/api/v1/admin/home"))
				.andExpect(jsonPath("$.data.featuredProductIds[1]").value(hidden))
				.andExpect(jsonPath("$.data.heroSlides[0].imageUrl").value("/files/hero.png"));
	}

	@Test
	void 저장한_적이_없으면_빈_편성이다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/home"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.heroSlides.length()").value(0))
				.andExpect(jsonPath("$.data.featuredProductIds.length()").value(0))
				.andExpect(jsonPath("$.data.ctaBand").doesNotExist());
		mockMvc.perform(get("/api/v1/home").with(anonymous()))
				.andExpect(jsonPath("$.data.promoBanners.length()").value(0))
				.andExpect(jsonPath("$.data.featuredProducts.length()").value(0))
				.andExpect(jsonPath("$.data.ctaBand").doesNotExist());
	}

	@Test
	void 외부_이미지와_사이트_밖_이동_경로와_없는_상품과_겹치는_ID는_거절한다() throws Exception {
		String valid = layout(List.of());
		for (String imageUrl : List.of("https://evil.example/hero.png", "/assets/../files/x.png", "/files/a/b.png")) {
			save(valid.replace("/files/hero.png", imageUrl))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_HOME_IMAGE_URL"));
		}
		// JSON 안의 값이라 역슬래시는 두 번 적었다(/\evil.example).
		for (String destination : List.of("javascript:alert(1)", "//evil.example", "/\\\\evil.example",
				"https://evil.example", "/a b")) {
			save(valid.replace("\"/category/ppe\"", "\"" + destination + "\""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		}
		long deleted = insertProduct("삭제한 상품", "DELETED");
		for (long productId : List.of(999_999L, deleted)) {
			save(layout(List.of(productId)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		}
		save(valid.replace("\"benefit-2\"", "\"benefit-1\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("HOME_ITEM_ID_DUPLICATED"));
		// 통째로 바꾸므로 빠진 목록은 지운다는 뜻으로 보지 않고 거절한다.
		save("""
				{"heroSlides":[],"serviceBenefits":[],"featuredProductIds":[],"ctaBand":null}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("promoBanners"));
		save(valid.replace("\"dark\"", "\"red\"")).andExpect(status().isBadRequest());
	}

	@Test
	void 홈_편성의_업로드_이미지는_참조_없는_이미지_정리가_지우지_않게_알린다() throws Exception {
		save(layout(List.of())).andExpect(status().isOk());
		entityManager.flush();

		assertThat(homeLayoutImageReferences.findAllImageUrls())
				.containsExactly("/files/hero.png", "/assets/web/promo-concrete-v2.webp");
	}

	@Test
	@WithAnonymousUser
	void 비회원은_홈_편성을_바꿀_수_없다() throws Exception {
		save(layout(List.of())).andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	void 일반_회원은_홈_편성을_바꿀_수_없다() throws Exception {
		save(layout(List.of())).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/home")).andExpect(status().isForbidden());
	}

	private ResultActions save(String body) throws Exception {
		return mockMvc.perform(put("/api/v1/admin/home").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String layout(List<Long> featuredProductIds) {
		String ids = featuredProductIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		return """
				{
				  "heroSlides": [{
				    "id": "hero-1", "eyebrow": "SAFE TOMORROW", "title": ["사람이 있는 곳에,", "더 안전한 내일을."],
				    "description": "현장의 안전이, 모두의 일상을 지킵니다.\\n이음산업건설이 함께합니다.",
				    "tagline": ["안전이", "연결하는"], "imageUrl": "/files/hero.png", "destination": "/category/ppe",
				    "ctaLabel": "제품 둘러보기", "secondaryCta": {"label": "회사소개 보기", "destination": "/about"}
				  }],
				  "promoBanners": [{
				    "id": "promo-1", "title": ["작은 변화가", "큰 안전을 만듭니다."], "description": "안전 솔루션",
				    "imageUrl": "/assets/web/promo-concrete-v2.webp", "tone": "dark", "destination": "/search?keyword=안전모"
				  }],
				  "serviceBenefits": [
				    {"id": "benefit-1", "title": "5만 원 이상 무료배송", "description": "제주·도서산간 제외"},
				    {"id": "benefit-2", "title": "평일 오후 2시 이전 결제 시 당일 출고"}
				  ],
				  "featuredProductIds": [%s],
				  "ctaBand": {"eyebrow": "QUOTE", "title": "대량 구매는 견적으로 상담하세요",
				              "cta": {"label": "견적 문의하기", "destination": "/quotes/new"}}
				}
				""".formatted(ids);
	}

	private long insertProduct(String name, String status) {
		jdbcTemplate.update("""
				insert into product (name, brand, base_price, price_visible, status, created_at, updated_at)
				values (?, '세이프가드', 10000, true, ?, now(), now())
				""", name, status);
		return jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
	}
}
