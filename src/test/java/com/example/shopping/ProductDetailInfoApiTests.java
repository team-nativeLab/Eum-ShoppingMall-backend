package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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

import com.example.shopping.domain.product.repository.ProductRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** 상품 상세 화면 정보(갤러리·사양·모델명·요약·원산지·배송 예정·설명 서식·상품 코드). 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class ProductDetailInfoApiTests {

	private static final String PRODUCT = """
			{"name":"ABS 안전모","basePrice":30000,"description":"**충격 흡수** 내피","descriptionFormat":"RICH_V1",
			 "skus":[{"optionName":"흰색","price":30000,"stock":10}],
			 "productCode":"PRD-1001","modelName":"SG-H100","summary":"현장 표준 안전모",
			 "countryOfOrigin":"대한민국","shippingEstimate":"평일 기준 2~3일 이내 출고",
			 "images":[{"url":"/files/helmet-front.png","alt":"정면"},{"url":"/files/helmet-side.png"}],
			 "specifications":[{"label":"재질","value":"ABS"},{"label":"중량","value":"420g"}]}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 관리자가_저장한_상세_정보가_고객_상품_상세에_순서대로_나온다() throws Exception {
		long id = idOf(create(PRODUCT)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.productCode").value("PRD-1001"))
				.andExpect(jsonPath("$.data.imageUrl").value("/files/helmet-front.png"))
				.andExpect(jsonPath("$.data.images[1].alt").value("")));

		mockMvc.perform(get("/api/v1/products/{id}", id).with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.modelName").value("SG-H100"))
				.andExpect(jsonPath("$.data.summary").value("현장 표준 안전모"))
				.andExpect(jsonPath("$.data.countryOfOrigin").value("대한민국"))
				.andExpect(jsonPath("$.data.shippingEstimate").value("평일 기준 2~3일 이내 출고"))
				.andExpect(jsonPath("$.data.descriptionFormat").value("RICH_V1"))
				.andExpect(jsonPath("$.data.images[*].url").value(contains("/files/helmet-front.png",
						"/files/helmet-side.png")))
				.andExpect(jsonPath("$.data.images[0].alt").value("정면"))
				.andExpect(jsonPath("$.data.specifications[*].label").value(contains("재질", "중량")))
				.andExpect(jsonPath("$.data.specifications[1].value").value("420g"))
				// 상품 코드는 관리용이라 고객 응답에 없다.
				.andExpect(jsonPath("$.data.productCode").doesNotExist());
		mockMvc.perform(get("/api/v1/admin/products").param("keyword", "ABS"))
				.andExpect(jsonPath("$.data.content[0].productCode").value("PRD-1001"));
	}

	@Test
	void 수정은_보낸_항목만_바꾸고_갤러리를_비우면_대표_이미지도_지운다() throws Exception {
		long id = idOf(create(PRODUCT));

		update(id, """
				{"modelName":"","images":[{"url":"/files/helmet-side.png","alt":"측면"}]}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.modelName").doesNotExist())
				.andExpect(jsonPath("$.data.imageUrl").value("/files/helmet-side.png"))
				.andExpect(jsonPath("$.data.images.length()").value(1))
				// 보내지 않은 사양·요약은 그대로다.
				.andExpect(jsonPath("$.data.specifications.length()").value(2))
				.andExpect(jsonPath("$.data.summary").value("현장 표준 안전모"));
		update(id, """
				{"images":[],"specifications":[],"descriptionFormat":"PLAIN"}
				""")
				.andExpect(jsonPath("$.data.imageUrl").doesNotExist())
				.andExpect(jsonPath("$.data.images.length()").value(0))
				.andExpect(jsonPath("$.data.specifications.length()").value(0))
				.andExpect(jsonPath("$.data.descriptionFormat").value("PLAIN"));
	}

	@Test
	void 외부_이미지와_겹치는_상품_코드와_형식이_틀린_상세_정보는_거절한다() throws Exception {
		long id = idOf(create(PRODUCT));

		create(PRODUCT.replace("PRD-1001", "PRD-2002").replace("/files/helmet-side.png",
				"https://evil.example/x.png"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_IMAGE_URL"));
		create(PRODUCT.replace("ABS 안전모", "다른 안전모"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRODUCT_CODE_DUPLICATED"));
		long other = idOf(create(PRODUCT.replace("PRD-1001", "PRD-3003")));
		update(other, "{\"productCode\":\"PRD-1001\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRODUCT_CODE_DUPLICATED"));
		// 자기 코드를 그대로 보내는 것은 괜찮다.
		update(id, "{\"productCode\":\"PRD-1001\"}").andExpect(status().isOk());

		String elevenImages = "[" + "{\"url\":\"/files/a.png\"},".repeat(10) + "{\"url\":\"/files/a.png\"}]";
		for (String body : new String[] {
				"{\"images\":" + elevenImages + "}",
				"{\"specifications\":[{\"label\":\" \",\"value\":\"ABS\"}]}",
				"{\"productCode\":\"코드 1\"}",
				"{\"descriptionFormat\":\"HTML\"}" }) {
			update(id, body).andExpect(status().isBadRequest());
		}
	}

	@Test
	void 갤러리_이미지는_참조_없는_이미지_정리가_지우지_않게_알린다() throws Exception {
		create(PRODUCT).andExpect(status().isCreated());
		entityManager.flush();

		assertThat(productRepository.findAllImageUrls())
				.contains("/files/helmet-front.png", "/files/helmet-side.png");
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions update(long id, String body) throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/products/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}
}
