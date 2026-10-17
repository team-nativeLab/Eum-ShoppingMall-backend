package com.example.shopping;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** 로그인한 회원이 쓰는 API (내 정보, 장바구니, 문의, 견적)와 공개 콘텐츠 API */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerApiTests {

	private static final String PASSWORD = "test-pass-1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// ───────── 내 정보 ─────────

	@Test
	void 내_정보를_조회하고_이름과_연락처를_수정한다() throws Exception {
		long memberId = insertMember("hong@example.com", "ACTIVE");

		mockMvc.perform(get("/api/v1/me").with(member(memberId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("hong@example.com"))
				.andExpect(content().string(not(containsString("password"))));

		mockMvc.perform(patch("/api/v1/me").with(member(memberId)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"홍길순","phone":"010-9999-8888"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("홍길순"))
				.andExpect(jsonPath("$.data.phone").value("01099998888"))
				.andExpect(jsonPath("$.data.email").value("hong@example.com"));
		mockMvc.perform(patch("/api/v1/me").with(member(memberId)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"phone":""}
								"""))
				.andExpect(jsonPath("$.data.name").value("홍길순"))
				.andExpect(jsonPath("$.data.phone").doesNotExist());
		mockMvc.perform(patch("/api/v1/me").with(member(memberId)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"  ","phone":"abc"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 정지되거나_없는_회원의_토큰으로는_내_정보를_볼_수_없다() throws Exception {
		long suspendedId = insertMember("suspended@example.com", "SUSPENDED");

		mockMvc.perform(get("/api/v1/me").with(member(suspendedId))).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me").with(member(999_999))).andExpect(status().isUnauthorized());
	}

	// ───────── 장바구니 ─────────

	@Test
	void 장바구니에_담고_수량과_선택을_바꾸면_합계가_다시_계산된다() throws Exception {
		long helmetSku = insertProductWithSku("안전모", 30000, true, "ON_SALE", 10);
		long vestSku = insertProductWithSku("안전 조끼", 20000, true, "ON_SALE", 10);
		RequestPostProcessor me = member(1);

		addToCart(me, helmetSku, 2);
		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(vestSku, 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.orderableCount").value(2))
				.andExpect(jsonPath("$.data.totalAmount").value(80000));

		// 같은 옵션을 또 담으면 수량이 더해진다.
		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(helmetSku, 1)))
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].quantity").value(3))
				.andExpect(jsonPath("$.data.totalAmount").value(110000));

		mockMvc.perform(patch("/api/v1/cart/items/{skuId}", helmetSku).with(me)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"quantity":1}
								"""))
				.andExpect(jsonPath("$.data.totalAmount").value(50000));
		mockMvc.perform(patch("/api/v1/cart/items/{skuId}", vestSku).with(me)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"selected":false}
								"""))
				.andExpect(jsonPath("$.data.orderableCount").value(1))
				.andExpect(jsonPath("$.data.totalAmount").value(30000));

		mockMvc.perform(delete("/api/v1/cart/items/{skuId}", helmetSku).with(me))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.totalAmount").value(0));
		mockMvc.perform(get("/api/v1/cart").with(me))
				.andExpect(jsonPath("$.data.items[0].productName").value("안전 조끼"));
	}

	@Test
	void 판매_중지한_옵션은_고객에게_숨기고_기존_장바구니에서는_주문할_수_없다() throws Exception {
		long memberId = insertMember("sku-retire@example.com", "ACTIVE");
		RequestPostProcessor admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
		String created = mockMvc.perform(post("/api/v1/admin/products").with(admin)
					.contentType(MediaType.APPLICATION_JSON).content("""
							{"name":"옵션 중지 테스트","basePrice":1000,
							 "skus":[{"optionName":"기본","price":1000,"stock":5},
							         {"optionName":"대형","price":1200,"stock":5}]}
							"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long productId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
		long retiredSkuId = ((Number) JsonPath.read(created, "$.data.skus[0].id")).longValue();
		long remainingSkuId = ((Number) JsonPath.read(created, "$.data.skus[1].id")).longValue();

		addToCart(member(memberId), retiredSkuId, 1);
		mockMvc.perform(delete("/api/v1/admin/products/{productId}/skus/{skuId}", productId, retiredSkuId)
					.with(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.skus[0].active").value(false));

		mockMvc.perform(get("/api/v1/products/{productId}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.skus.length()").value(1))
				.andExpect(jsonPath("$.data.skus[0].id").value(remainingSkuId));
		mockMvc.perform(get("/api/v1/products").param("query", "옵션 중지 테스트"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].optionRequired").value(false))
				.andExpect(jsonPath("$.data.content[0].defaultSkuId").value(remainingSkuId));
		mockMvc.perform(get("/api/v1/cart").with(member(memberId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].status").value("NOT_ON_SALE"))
				.andExpect(jsonPath("$.data.orderableCount").value(0));
		mockMvc.perform(post("/api/v1/cart/items").with(member(memberId)).contentType(MediaType.APPLICATION_JSON)
					.content(cartItem(retiredSkuId, 1)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		mockMvc.perform(delete("/api/v1/admin/products/{productId}/skus/{skuId}", productId, remainingSkuId)
					.with(admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 장바구니_결제예정금액은_무료배송_경계와_주문_계산을_따른다() throws Exception {
		long[] itemAmounts = { 99_999, 100_000, 100_001 };
		long[] discounts = { 999, 1_000, 1_000 };
		long[] shippingFees = { 5_000, 0, 0 };
		long[] paymentAmounts = { 104_000, 99_000, 99_001 };
		RequestPostProcessor me = member(1);

		for (int i = 0; i < itemAmounts.length; i++) {
			jdbcTemplate.update("delete from cart_item where member_id = ?", 1L);
			long sku = insertProductWithSku("배송 정책 테스트 " + i, itemAmounts[i], true, "ON_SALE", 1);
			mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(sku, 1)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.itemAmount").value(itemAmounts[i]))
					.andExpect(jsonPath("$.data.totalAmount").value(itemAmounts[i]))
					.andExpect(jsonPath("$.data.discountAmount").value(discounts[i]))
					.andExpect(jsonPath("$.data.shippingFee").value(shippingFees[i]))
					.andExpect(jsonPath("$.data.paymentAmount").value(paymentAmounts[i]));
		}
	}

	@Test
	void 금액_미표시와_품절_상품은_장바구니에서_주문_불가로_표시되고_가격이_새지_않는다() throws Exception {
		long hiddenSku = insertProductWithSku("견적 전용 장비", 987654, false, "ON_SALE", 10);
		long shortSku = insertProductWithSku("안전화", 50000, true, "ON_SALE", 1);
		long soldOutSku = insertProductWithSku("보안경", 7000, true, "SOLD_OUT", 0);
		RequestPostProcessor me = member(1);

		addToCart(me, hiddenSku, 1);
		addToCart(me, shortSku, 2);
		addToCart(me, soldOutSku, 1);

		mockMvc.perform(get("/api/v1/cart").with(me))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].status").value("PRICE_HIDDEN"))
				.andExpect(jsonPath("$.data.items[0].price").doesNotExist())
				.andExpect(jsonPath("$.data.items[1].status").value("OUT_OF_STOCK"))
				.andExpect(jsonPath("$.data.items[2].status").value("OUT_OF_STOCK"))
				.andExpect(jsonPath("$.data.orderableCount").value(0))
				.andExpect(jsonPath("$.data.totalAmount").value(0))
				.andExpect(jsonPath("$.data.discountAmount").value(0))
				.andExpect(jsonPath("$.data.shippingFee").value(0))
				.andExpect(jsonPath("$.data.paymentAmount").value(0))
				.andExpect(content().string(not(containsString("987654"))));
	}

	@Test
	void 남의_장바구니는_보이지_않고_바꿀_수도_없다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, "ON_SALE", 10);
		addToCart(member(1), sku, 2);

		mockMvc.perform(get("/api/v1/cart").with(member(2)))
				.andExpect(jsonPath("$.data.items.length()").value(0));
		mockMvc.perform(patch("/api/v1/cart/items/{skuId}", sku).with(member(2))
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"quantity":99}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
		mockMvc.perform(delete("/api/v1/cart/items/{skuId}", sku).with(member(2)))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/cart").with(member(1)))
				.andExpect(jsonPath("$.data.items[0].quantity").value(2));
	}

	@Test
	void 담을_수_없는_상품은_거부하고_수량에는_상한이_없다() throws Exception {
		long sku = insertProductWithSku("안전모", 30000, true, "ON_SALE", 10);
		long hiddenProductSku = insertProductWithSku("비공개 상품", 30000, true, "HIDDEN", 10);
		long deletedProductSku = insertProductWithSku("삭제된 상품", 30000, true, "DELETED", 10);
		RequestPostProcessor me = member(1);

		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(hiddenProductSku, 1)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(deletedProductSku, 1)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(999_999, 1)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(sku, 0)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(sku, -5)))
				.andExpect(status().isBadRequest());

		// 수량에는 상한이 없다. 재고(10개)보다 많이 담으면 담기는 되지만 재고 부족으로 표시돼 주문할 수 없다.
		addToCart(me, sku, 60);
		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(sku, 60)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].quantity").value(120))
				.andExpect(jsonPath("$.data.items[0].status").value("OUT_OF_STOCK"));
		mockMvc.perform(patch("/api/v1/cart/items/{skuId}", sku).with(me).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"quantity":5000}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].quantity").value(5000));

		// 옵션 하나가 가질 수 있는 재고 상한(10억 개)보다 많이는 담을 수 없다. 더한 값이 int 범위를 넘어도 마찬가지다.
		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(sku, Integer.MAX_VALUE)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
		mockMvc.perform(get("/api/v1/cart").with(me))
				.andExpect(jsonPath("$.data.items[0].quantity").value(5000));
	}

	@Test
	void 장바구니에는_상품을_50종까지_담을_수_있다() throws Exception {
		RequestPostProcessor me = member(1);
		for (int i = 1; i <= 50; i++) {
			addToCart(me, insertProductWithSku("상품 " + i, 1000, true, "ON_SALE", 10), 1);
		}
		long oneMore = insertProductWithSku("상품 51", 1000, true, "ON_SALE", 10);

		mockMvc.perform(post("/api/v1/cart/items").with(me).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(oneMore, 1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CART_LIMIT_EXCEEDED"));
	}

	// ───────── 1:1 문의 ─────────

	@Test
	void 비회원은_연락처를_적어야_문의할_수_있고_회원은_자기_문의만_조회한다() throws Exception {
		String withoutContact = """
				{"inquiryType":"배송","title":"배송 문의","content":"언제 오나요?"}
				""";
		mockMvc.perform(post("/api/v1/support/inquiries").contentType(MediaType.APPLICATION_JSON)
						.content(withoutContact))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/support/inquiries").contentType(MediaType.APPLICATION_JSON).content("""
						{"inquiryType":"배송","title":"비회원 문의","content":"언제 오나요?","contactEmail":"guest@example.com"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("RECEIVED"));

		// 회원은 연락처 없이도 접수할 수 있다 (로그인해서 답변을 확인한다).
		mockMvc.perform(post("/api/v1/support/inquiries").with(member(1)).contentType(MediaType.APPLICATION_JSON)
						.content(withoutContact))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/support/inquiries").with(member(1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].title").value("배송 문의"));
		mockMvc.perform(get("/api/v1/support/inquiries").with(member(2)))
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	// ───────── 견적 ─────────

	@Test
	void 견적_문의를_접수하고_회원은_자기_내역만_조회한다() throws Exception {
		long hiddenPriceProduct = productIdOf(insertProductWithSku("견적 전용 장비", 500000, false, "ON_SALE", 1));
		long privateProduct = productIdOf(insertProductWithSku("비공개 상품", 500000, true, "HIDDEN", 1));

		mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content("""
						{"companyName":"이음건설","managerName":"김담당","phone":"02-1234-5678","content":"견적 요청"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.phone").value("0212345678"))
				.andExpect(jsonPath("$.data.status").value("RECEIVED"));
		mockMvc.perform(post("/api/v1/quotes").with(member(1)).contentType(MediaType.APPLICATION_JSON).content("""
						{"productId":%d,"companyName":"이음건설","managerName":"김담당","phone":"0212345678",
						 "content":"이 상품 견적 부탁드립니다."}
						""".formatted(hiddenPriceProduct)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.productId").value(hiddenPriceProduct));

		// 고객에게 보이지 않는 상품은 없는 상품과 똑같이 응답한다.
		mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content("""
						{"productId":%d,"companyName":"이음건설","managerName":"김담당","phone":"0212345678","content":"견적"}
						""".formatted(privateProduct)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content("""
						{"companyName":"이음건설","managerName":"김담당","phone":"전화번호","content":"견적"}
						"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/quotes").with(member(1)))
				.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/v1/quotes").with(member(2)))
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	// ───────── 홈·콘텐츠 ─────────

	@Test
	void 홈은_노출_중인_배너와_고객에게_보이는_신상품만_내려준다() throws Exception {
		jdbcTemplate.update("""
				insert into banner (image_url, link_url, sort_order, visible, created_at, updated_at) values
				('/files/second.png', null, 2, true, now(), now()),
				('/files/first.png', '/products/1', 1, true, now(), now()),
				('/files/hidden.png', null, 0, false, now(), now())
				""");
		insertProductWithSku("안전모", 30000, true, "ON_SALE", 10);
		insertProductWithSku("견적 전용 장비", 987654, false, "ON_SALE", 10);
		insertProductWithSku("비공개 상품", 10000, true, "HIDDEN", 10);

		mockMvc.perform(get("/api/v1/home"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.banners.length()").value(2))
				.andExpect(jsonPath("$.data.banners[0].imageUrl").value("/files/first.png"))
				.andExpect(jsonPath("$.data.newProducts.length()").value(2))
				.andExpect(jsonPath("$.data.newProducts[0].name").value("견적 전용 장비"))
				.andExpect(content().string(not(containsString("987654"))))
				.andExpect(content().string(not(containsString("비공개 상품"))));
	}

	@Test
	void 회사소개는_등록돼_있으면_내려주고_없으면_404다() throws Exception {
		mockMvc.perform(get("/api/v1/contents/company"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"));

		jdbcTemplate.update("""
				insert into site_content (code, title, body, created_at, updated_at)
				values ('company', '회사소개', '이음산업건설입니다.', now(), now())
				""");
		mockMvc.perform(get("/api/v1/contents/company"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("회사소개"))
				.andExpect(jsonPath("$.data.body").value("이음산업건설입니다."));
	}

	@Test
	void 앱_설정은_로그인_없이_조회된다() throws Exception {
		mockMvc.perform(get("/api/v1/app/config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.minVersion").value("1.0.0"))
				.andExpect(jsonPath("$.data.maintenance").value(false));
	}

	@Test
	void 요청_ID를_검증해_응답에_돌려준다() throws Exception {
		mockMvc.perform(get("/api/v1/app/config").header("X-Request-Id", "support.case_17-abc"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "support.case_17-abc"));

		String generated = mockMvc.perform(get("/api/v1/app/config").header("X-Request-Id", "invalid request id"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getHeader("X-Request-Id");
		org.assertj.core.api.Assertions.assertThat(generated).matches("[0-9a-f-]{36}");
	}

	// ───────── 도우미 ─────────

	/** 해당 회원으로 로그인한 요청. 실제 토큰 발급·검증은 AuthApiTests에서 다룬다. */
	private RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private void addToCart(RequestPostProcessor member, long skuId, int quantity) throws Exception {
		mockMvc.perform(post("/api/v1/cart/items").with(member).contentType(MediaType.APPLICATION_JSON)
						.content(cartItem(skuId, quantity)))
				.andExpect(status().isOk());
	}

	private String cartItem(long skuId, int quantity) {
		return """
				{"skuId":%d,"quantity":%d}
				""".formatted(skuId, quantity);
	}

	private long insertMember(String email, String status) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, phone, role, status, created_at, updated_at)
				values (?, ?, '홍길동', '01012345678', 'USER', ?, now(), now())
				""", email, passwordEncoder.encode(PASSWORD), status);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}

	/** 옵션이 하나인 상품을 만들고 그 옵션(SKU)의 ID를 돌려준다. 옵션 가격은 상품 가격과 같다. */
	private long insertProductWithSku(String name, long price, boolean priceVisible, String status, int stock) {
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values (?, ?, ?, ?, now(), now())
				""", name, price, priceVisible, status);
		Long productId = jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
		jdbcTemplate.update("""
				insert into product_sku (product_id, option_name, price, stock, version) values (?, '기본', ?, ?, 0)
				""", productId, price, stock);
		return jdbcTemplate.queryForObject("select id from product_sku where product_id = ?", Long.class, productId);
	}

	private long productIdOf(long skuId) {
		return jdbcTemplate.queryForObject("select product_id from product_sku where id = ?", Long.class, skuId);
	}
}
