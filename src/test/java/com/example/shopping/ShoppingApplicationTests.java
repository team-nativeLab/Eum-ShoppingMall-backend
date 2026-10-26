package com.example.shopping;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShoppingApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void 상품_목록은_로그인_없이_조회된다() throws Exception {
		insertProduct("공개 상품", 15000, true);

		mockMvc.perform(get("/api/v1/products").param("query", "공개"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].price").value(15000));
	}

	@Test
	void 금액_미표시_상품은_가격을_응답에_싣지_않는다() throws Exception {
		Long productId = insertProduct("비공개 상품", 99000, false);

		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.priceVisible").value(false))
				.andExpect(content().string(not(containsString("99000"))));
	}

	@Test
	void 없는_상품은_공통_에러_포맷으로_404를_돌려준다() throws Exception {
		mockMvc.perform(get("/api/v1/products/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void 로그인이_필요한_API는_공통_에러_포맷으로_401을_돌려준다() throws Exception {
		mockMvc.perform(get("/api/v1/orders"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@WithMockUser(roles = "USER")
	void 없는_주소나_잘못된_요청_방식_형식도_공통_에러_포맷으로_응답한다() throws Exception {
		mockMvc.perform(get("/api/v1/no-such-api"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("요청한 주소를 찾을 수 없습니다."));
		mockMvc.perform(put("/api/v1/auth/login"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("로그인"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void 비회원이_장바구니를_쓰려고_하면_로그인이나_회원가입을_안내한다() throws Exception {
		mockMvc.perform(post("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON)
						.content("{\"skuId\":1,\"quantity\":1}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"))
				.andExpect(jsonPath("$.message").value("로그인이나 회원가입을 해주세요."));
		mockMvc.perform(get("/api/v1/cart"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));

		// 토큰을 보냈는데 그 토큰이 잘못된 경우는 비회원이 아니므로 일반 인증 오류로 응답한다.
		mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer broken-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void 삭제된_상품은_목록과_상세에서_빠진다() throws Exception {
		Long productId = insertProduct("삭제된 상품", 15000, true, "DELETED");

		mockMvc.perform(get("/api/v1/products").param("query", "삭제된"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(status().isNotFound());
	}

	@Test
	void 견적_문의와_1대1_문의는_비회원도_접수할_수_있다() throws Exception {
		// 빈 요청이라 400이지만, 401이 아니라는 것은 로그인 없이 컨트롤러까지 도달했다는 뜻이다.
		mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/support/inquiries").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 내_문의_내역_조회는_로그인이_필요하고_로그아웃은_로그인_없이도_된다() throws Exception {
		mockMvc.perform(get("/api/v1/quotes")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/support/inquiries")).andExpect(status().isUnauthorized());
		// 로그아웃은 쿠키의 Refresh 토큰만 폐기하므로 Access 토큰이 없거나 만료돼도 처리된다.
		mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isOk());
	}

	@Test
	void 관리자_API는_로그인_없이는_401을_돌려준다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/uploads")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/uploads/presigned-url")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/uploads/complete")).andExpect(status().isUnauthorized());
	}

	@Test
	void 관리자_로그인은_로그인_없이_호출할_수_있다() throws Exception {
		// 빈 요청이라 400이지만, 401이 아니라는 것은 로그인 없이 컨트롤러까지 도달했다는 뜻이다.
		mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "USER")
	void 일반_회원은_관리자_API와_파일_업로드를_호출할_수_없다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/orders"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(post("/api/v1/uploads")).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/uploads/presigned-url")).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/uploads/complete")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자는_관리자_API와_파일_업로드를_호출할_수_있다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/orders")).andExpect(status().isOk());
		// 파일 없이 보내 415지만, 403이 아니라는 것은 권한 검사를 통과했다는 뜻이다.
		mockMvc.perform(post("/api/v1/uploads")).andExpect(status().isUnsupportedMediaType());
	}

	private Long insertProduct(String name, long basePrice, boolean priceVisible) {
		return insertProduct(name, basePrice, priceVisible, "ON_SALE");
	}

	private Long insertProduct(String name, long basePrice, boolean priceVisible, String status) {
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values (?, ?, ?, ?, now(), now())
				""", name, basePrice, priceVisible, status);
		return jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
	}
}
