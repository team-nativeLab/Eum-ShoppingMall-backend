package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.example.shopping.domain.member.repository.MemberRepository;
import com.example.shopping.domain.member.service.AdminAccountInitializer;
import com.example.shopping.domain.member.service.AdminBootstrapProperties;
import com.example.shopping.domain.member.service.RefreshTokenService;
import com.jayway.jsonpath.JsonPath;

/** 관리자 API. 따로 표시하지 않은 테스트는 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class AdminApiTests {

	private static final String PASSWORD = "test-pass-1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private RefreshTokenService refreshTokenService;

	// ───────── 관리자 로그인·계정 ─────────

	@Test
	@WithAnonymousUser
	void 관리자_로그인은_관리자_계정에만_토큰을_준다() throws Exception {
		insertMember("admin@example.com", "관리자", "ADMIN");
		insertMember("member@example.com", "홍길동", "USER");

		String body = adminLogin("admin@example.com", PASSWORD)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.data.accessToken");
		mockMvc.perform(get("/api/v1/admin/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());

		// 일반 회원은 비밀번호가 맞아도 없는 계정과 똑같이 거절한다.
		adminLogin("member@example.com", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		adminLogin("admin@example.com", "wrong-pass-1234")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	@WithAnonymousUser
	void 설정된_초기_관리자_계정은_없을_때만_만들고_기존_계정은_건드리지_않는다() throws Exception {
		insertMember("existing@example.com", "기존회원", "USER");
		long existingAdminId = insertMember("existing-admin@example.com", "기존 관리자", "ADMIN");
		refreshTokenService.issue(existingAdminId, 0);

		AdminAccountInitializer initializer = new AdminAccountInitializer(
				new AdminBootstrapProperties("Boot@Example.com", PASSWORD), memberRepository, passwordEncoder,
				refreshTokenService);
		initializer.run(null);
		initializer.run(null);
		new AdminAccountInitializer(new AdminBootstrapProperties("existing@example.com", "another-pass-1234"),
				memberRepository, passwordEncoder, refreshTokenService).run(null);
		new AdminAccountInitializer(new AdminBootstrapProperties("", ""), memberRepository, passwordEncoder,
				refreshTokenService).run(null);

		new AdminAccountInitializer(new AdminBootstrapProperties("existing-admin@example.com", PASSWORD),
				memberRepository, passwordEncoder, refreshTokenService).run(null);

		assertThat(jdbcTemplate.queryForObject("select count(*) from member", Long.class)).isEqualTo(3);
		assertThat(roleOf("boot@example.com")).isEqualTo("SUPER_ADMIN");
		assertThat(roleOf("existing@example.com")).isEqualTo("USER");
		assertThat(roleOf("existing-admin@example.com")).isEqualTo("SUPER_ADMIN");
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class,
				existingAdminId)).isZero();
		adminLogin("boot@example.com", PASSWORD).andExpect(status().isOk());
		// 기존 회원의 비밀번호가 설정값으로 덮어써지지 않았다.
		adminLogin("existing@example.com", "another-pass-1234").andExpect(status().isUnauthorized());
	}

	@Test
	void 최고관리자만_역할을_바꾸고_변경된_회원의_토큰을_폐기한다() throws Exception {
		long superAdminId = insertMember("super@example.com", "최고관리자", "SUPER_ADMIN");
		long memberId = insertMember("role-target@example.com", "대상 회원", "ADMIN");
		String accessToken = JsonPath.read(adminLogin("role-target@example.com", PASSWORD)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.accessToken");
		refreshTokenService.issue(memberId, 0);

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", memberId).with(superAdmin(superAdminId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"role":"USER"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("USER"));

		assertThat(roleOf("role-target@example.com")).isEqualTo("USER");
		assertThat(jdbcTemplate.queryForObject("select access_token_version from member where id = ?", Long.class,
				memberId)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class,
				memberId)).isZero();
		mockMvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());

		long adminId = insertMember("ordinary-admin@example.com", "관리자", "ADMIN");
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", memberId).with(admin(adminId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"role":"USER"}
						"""))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", superAdminId).with(superAdmin(superAdminId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"role":"USER"}
						"""))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", memberId).with(superAdmin(superAdminId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"role":"SUPER_ADMIN"}
						"""))
				.andExpect(status().isBadRequest());

		entityManager.flush();
		jdbcTemplate.update("update member set status = 'SUSPENDED' where id = ?", memberId);
		entityManager.clear();
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", memberId).with(superAdmin(superAdminId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"role":"ADMIN"}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
		jdbcTemplate.update("update member set status = 'WITHDRAWN' where id = ?", memberId);
		entityManager.clear();
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", memberId).with(superAdmin(superAdminId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"role":"USER"}
						"""))
				.andExpect(status().isForbidden());
	}

	// ───────── 회원 ─────────

	@Test
	void 회원을_검색하고_상세에서_연락처와_주문_이력을_본다() throws Exception {
		long hongId = insertMember("hong@example.com", "홍길동", "USER");
		insertMember("kim@example.com", "김철수", "USER");
		insertOrder("ORD-HONG-1", hongId, "PAID", 50000, LocalDateTime.now());

		mockMvc.perform(get("/api/v1/admin/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[0].phone").doesNotExist())
				.andExpect(content().string(not(containsString("password"))));
		mockMvc.perform(get("/api/v1/admin/users").param("keyword", "hong"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].name").value("홍길동"));
		mockMvc.perform(get("/api/v1/admin/users").param("keyword", "철수"))
				.andExpect(jsonPath("$.data.totalElements").value(1));

		mockMvc.perform(get("/api/v1/admin/users/{id}", hongId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.phone").value("01012345678"))
				.andExpect(jsonPath("$.data.recentOrders.length()").value(1))
				.andExpect(jsonPath("$.data.recentOrders[0].orderNo").value("ORD-HONG-1"))
				.andExpect(content().string(not(containsString("password"))));
		mockMvc.perform(get("/api/v1/admin/users/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void 관리자가_회원_계정을_정지하고_해제한다() throws Exception {
		long adminId = insertMember("status-admin@example.com", "운영자", "ADMIN");
		long memberId = insertMember("status-target@example.com", "대상", "USER");
		refreshTokenService.issue(memberId, 0);

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", memberId).with(admin(adminId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"status":"SUSPENDED","reason":"비정상 주문 반복"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SUSPENDED"))
				.andExpect(jsonPath("$.data.suspensionReason").value("비정상 주문 반복"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class,
				memberId)).isZero();

		mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", memberId).with(admin(adminId))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"status":"ACTIVE"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.suspensionReason").doesNotExist());
	}

	// ───────── 카테고리 ─────────

	@Test
	void 카테고리를_등록_수정하고_상위를_숨기면_하위도_고객_목록에서_빠진다() throws Exception {
		long parentId = idOf(postJson("/api/v1/admin/categories", """
				{"name":"안전용품","slug":"safety","description":"작업자의 안전을 위해","iconKey":"hard-hat",
				 "sortOrder":1}
				""").andExpect(status().isCreated()));
		postJson("/api/v1/admin/categories", """
				{"name":"안전모","slug":"head-protection","parentId":%d}
				""".formatted(parentId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.parentId").value(parentId))
				.andExpect(jsonPath("$.data.slug").value("head-protection"))
				.andExpect(jsonPath("$.data.active").value(true));
		mockMvc.perform(get("/api/v1/categories"))
				.andExpect(jsonPath("$.data.length()").value(2))
				// 순서(sortOrder)가 0인 하위 카테고리가 앞에 온다.
				.andExpect(jsonPath("$.data[1].slug").value("safety"))
				.andExpect(jsonPath("$.data[1].description").value("작업자의 안전을 위해"))
				.andExpect(jsonPath("$.data[1].iconKey").value("hard-hat"));

		patchJson("/api/v1/admin/categories/" + parentId, """
				{"name":"안전 용품","slug":"safety-supplies","iconKey":"","active":false}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("안전 용품"))
				.andExpect(jsonPath("$.data.slug").value("safety-supplies"))
				.andExpect(jsonPath("$.data.description").value("작업자의 안전을 위해"))
				.andExpect(jsonPath("$.data.iconKey").doesNotExist())
				.andExpect(jsonPath("$.data.sortOrder").value(1))
				.andExpect(jsonPath("$.data.active").value(false));

		mockMvc.perform(get("/api/v1/admin/categories")).andExpect(jsonPath("$.data.length()").value(2));
		mockMvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void 카테고리_주소_이름은_필수이고_다른_카테고리와_겹칠_수_없다() throws Exception {
		long helmetId = idOf(postJson("/api/v1/admin/categories", """
				{"name":"안전모","slug":"head-protection"}
				""").andExpect(status().isCreated()));
		long goggleId = idOf(postJson("/api/v1/admin/categories", """
				{"name":"보안경","slug":"eye-protection"}
				""").andExpect(status().isCreated()));

		postJson("/api/v1/admin/categories", """
				{"name":"주소 없음"}
				""").andExpect(status().isBadRequest());
		postJson("/api/v1/admin/categories", """
				{"name":"대문자","slug":"Head Protection"}
				""").andExpect(status().isBadRequest());
		postJson("/api/v1/admin/categories", """
				{"name":"안전모 2","slug":"head-protection"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CATEGORY_SLUG_EXISTS"));
		patchJson("/api/v1/admin/categories/" + goggleId, """
				{"slug":"head-protection"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CATEGORY_SLUG_EXISTS"));
		// 자기 자신의 주소 이름을 그대로 보내는 것은 괜찮다.
		patchJson("/api/v1/admin/categories/" + helmetId, """
				{"slug":"head-protection","name":"산업용 안전모"}
				""").andExpect(status().isOk());
	}

	@Test
	void 하위_카테고리나_상품이_있는_카테고리는_삭제할_수_없다() throws Exception {
		long parentId = idOf(postJson("/api/v1/admin/categories", """
				{"name":"안전용품","slug":"safety"}
				"""));
		long childId = idOf(postJson("/api/v1/admin/categories", """
				{"name":"안전모","slug":"head-protection","parentId":%d}
				""".formatted(parentId)));
		long productId = idOf(postJson("/api/v1/admin/products", """
				{"name":"산업용 안전모","basePrice":35000,"categoryId":%d,"status":"HIDDEN"}
				""".formatted(childId)));

		mockMvc.perform(delete("/api/v1/admin/categories/{id}", parentId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));
		mockMvc.perform(delete("/api/v1/admin/categories/{id}", childId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));

		// 상품을 삭제(소프트 삭제)하면 그 카테고리는 비어 있는 것으로 본다.
		mockMvc.perform(delete("/api/v1/admin/products/{id}", productId)).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/admin/categories/{id}", childId)).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/admin/categories/{id}", parentId)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/categories")).andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(delete("/api/v1/admin/categories/{id}", parentId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
	}

	// ───────── 상품 ─────────

	@Test
	void 금액_미표시로_등록한_상품은_관리자에게만_가격이_보인다() throws Exception {
		long productId = idOf(postJson("/api/v1/admin/products", """
				{"name":"산업용 안전모","description":"충격 흡수","imageUrl":"/files/helmet.png",
				 "basePrice":35000,"priceVisible":false,
				 "skus":[{"optionName":"흰색","price":35000,"stock":10},{"optionName":"노랑","price":36000,"stock":0}]}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("ON_SALE"))
				.andExpect(jsonPath("$.data.basePrice").value(35000))
				.andExpect(jsonPath("$.data.skus.length()").value(2))
				.andExpect(jsonPath("$.data.skus[0].stock").value(10)));

		mockMvc.perform(get("/api/v1/admin/products/{id}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.basePrice").value(35000))
				.andExpect(jsonPath("$.data.skus[1].price").value(36000));
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.priceVisible").value(false))
				.andExpect(jsonPath("$.data.imageUrl").value("/files/helmet.png"))
				.andExpect(jsonPath("$.data.skus[1].soldOut").value(true))
				.andExpect(content().string(not(containsString("35000"))))
				.andExpect(content().string(not(containsString("36000"))));

		patchJson("/api/v1/admin/products/" + productId + "/visibility", """
				{"priceVisible":true}
				""").andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(jsonPath("$.data.price").value(35000));
	}

	@Test
	void 상품을_수정하고_비공개로_바꾸거나_삭제하면_고객_화면에서_빠진다() throws Exception {
		String created = postJson("/api/v1/admin/products", """
				{"name":"산업용 안전모","description":"충격 흡수","basePrice":35000,
				 "skus":[{"optionName":"흰색","price":35000,"stock":10}]}
				""").andReturn().getResponse().getContentAsString();
		long productId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
		long skuId = ((Number) JsonPath.read(created, "$.data.skus[0].id")).longValue();
		// 관리자가 화면을 연 뒤 주문이 들어와 현재 재고가 10 → 7로 줄어든 상태.
		jdbcTemplate.update("update product_sku set stock = 7, version = version + 1 where id = ?", skuId);
		entityManager.clear();

		patchJson("/api/v1/admin/products/" + productId, """
				{"name":"경량 안전모","status":"HIDDEN","description":"",
				 "skus":[{"id":%d,"optionName":"흰색","price":40000,"stock":5},
				         {"optionName":"파랑","price":41000,"stock":3}]}
				""".formatted(skuId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("경량 안전모"))
				.andExpect(jsonPath("$.data.status").value("HIDDEN"))
				.andExpect(jsonPath("$.data.description").doesNotExist())
				.andExpect(jsonPath("$.data.basePrice").value(35000))
				.andExpect(jsonPath("$.data.skus.length()").value(1))
				.andExpect(jsonPath("$.data.skus[0].price").value(35000))
				.andExpect(jsonPath("$.data.skus[0].stock").value(7))
				.andExpect(jsonPath("$.data.skus[0].active").value(true));

		patchJson("/api/v1/admin/products/" + productId + "/skus/" + skuId, """
				{"optionName":"흰색","price":40000}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.skus[0].price").value(40000))
				.andExpect(jsonPath("$.data.skus[0].stock").value(7));
		postJson("/api/v1/admin/products/" + productId + "/skus", """
				{"optionName":"파랑","price":41000,"stock":3}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.skus.length()").value(2))
				.andExpect(jsonPath("$.data.skus[1].optionName").value("파랑"));

		mockMvc.perform(get("/api/v1/products/{id}", productId)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/admin/products"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/v1/admin/products").param("status", "ON_SALE"))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/v1/admin/products").param("keyword", "경량"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].basePrice").value(35000));

		mockMvc.perform(delete("/api/v1/admin/products/{id}", productId)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/products"))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/v1/admin/products/{id}", productId)).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/admin/products/{id}", productId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void 고객에게_노출되는_상품에는_옵션이_필요하다() throws Exception {
		postJson("/api/v1/admin/products", """
				{"name":"옵션 없는 판매 상품","basePrice":5000}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		String hidden = postJson("/api/v1/admin/products", """
				{"name":"옵션 준비 중 상품","basePrice":5000,"status":"HIDDEN"}
				""").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long productId = ((Number) JsonPath.read(hidden, "$.data.id")).longValue();

		patchJson("/api/v1/admin/products/" + productId, """
				{"status":"ON_SALE"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 입고하면_현재_재고에_수량이_더해지고_그사이_주문으로_줄어든_재고를_덮어쓰지_않는다() throws Exception {
		String created = postJson("/api/v1/admin/products", """
				{"name":"산업용 안전모","basePrice":30000,
				 "skus":[{"optionName":"흰색","price":30000,"stock":10},{"optionName":"노랑","price":30000,"stock":0}]}
				""").andReturn().getResponse().getContentAsString();
		long productId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
		long whiteSku = ((Number) JsonPath.read(created, "$.data.skus[0].id")).longValue();
		long yellowSku = ((Number) JsonPath.read(created, "$.data.skus[1].id")).longValue();

		postJson(stockInUrl(productId, yellowSku), """
				{"quantity":5}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.skus[0].stock").value(10))
				.andExpect(jsonPath("$.data.skus[1].stock").value(5));

		// 관리자가 화면을 띄워 둔 사이 주문이 들어와 흰색 재고가 10 → 7로 줄어든 상황
		jdbcTemplate.update("update product_sku set stock = 7, version = version + 1 where id = ?", whiteSku);

		postJson(stockInUrl(productId, whiteSku), """
				{"quantity":5}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.skus[0].stock").value(12));
		mockMvc.perform(get("/api/v1/admin/products/{id}", productId))
				.andExpect(jsonPath("$.data.skus[0].stock").value(12))
				.andExpect(jsonPath("$.data.skus[1].stock").value(5));
		// 품절이던 옵션은 재고가 들어오면 고객 화면에서 품절이 풀린다.
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(jsonPath("$.data.skus[1].soldOut").value(false));
	}

	@Test
	void 잘못된_입고_요청은_거부한다() throws Exception {
		String first = postJson("/api/v1/admin/products", """
				{"name":"안전모","basePrice":30000,"skus":[{"optionName":"흰색","price":30000,"stock":10}]}
				""").andReturn().getResponse().getContentAsString();
		String second = postJson("/api/v1/admin/products", """
				{"name":"안전화","basePrice":50000,"skus":[{"optionName":"260","price":50000,"stock":10}]}
				""").andReturn().getResponse().getContentAsString();
		long productId = ((Number) JsonPath.read(first, "$.data.id")).longValue();
		long skuId = ((Number) JsonPath.read(first, "$.data.skus[0].id")).longValue();
		long otherProductSku = ((Number) JsonPath.read(second, "$.data.skus[0].id")).longValue();

		for (String body : new String[] { "{\"quantity\":0}", "{\"quantity\":-3}", "{\"quantity\":1000001}", "{}" }) {
			postJson(stockInUrl(productId, skuId), body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		}
		// 다른 상품의 옵션, 없는 상품
		postJson(stockInUrl(productId, otherProductSku), "{\"quantity\":1}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
		postJson(stockInUrl(999_999, skuId), "{\"quantity\":1}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

		// 재고 상한(10억 개)을 넘는 입고는 받지 않고 재고도 그대로 둔다.
		jdbcTemplate.update("update product_sku set stock = 999999999 where id = ?", skuId);
		postJson(stockInUrl(productId, skuId), "{\"quantity\":2}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("STOCK_LIMIT_EXCEEDED"));
		assertThat(jdbcTemplate.queryForObject("select stock from product_sku where id = ?", Integer.class, skuId))
				.isEqualTo(999_999_999);
		postJson(stockInUrl(productId, skuId), "{\"quantity\":1}").andExpect(status().isOk());

		// 삭제된 상품에는 입고할 수 없다.
		mockMvc.perform(delete("/api/v1/admin/products/{id}", productId)).andExpect(status().isOk());
		postJson(stockInUrl(productId, skuId), "{\"quantity\":1}").andExpect(status().isNotFound());
	}

	@Test
	void 잘못된_상품_요청은_거부한다() throws Exception {
		postJson("/api/v1/admin/products", """
				{"basePrice":1000}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("name"));
		postJson("/api/v1/admin/products", """
				{"name":"안전모","basePrice":-1}
				""").andExpect(status().isBadRequest());
		postJson("/api/v1/admin/products", """
				{"name":"안전모","basePrice":1000,"status":"DELETED"}
				""").andExpect(status().isBadRequest());
		postJson("/api/v1/admin/products", """
				{"name":"안전모","basePrice":1000,"imageUrl":"javascript:alert(1)"}
				""").andExpect(status().isBadRequest());
		postJson("/api/v1/admin/products", """
				{"name":"안전모","basePrice":1000,"skus":[{"optionName":"흰색","price":1000,"stock":-1}]}
				""").andExpect(status().isBadRequest());
		postJson("/api/v1/admin/products", """
				{"name":"안전모","basePrice":1000,"categoryId":999999}
				""")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

		long productId = idOf(postJson("/api/v1/admin/products", """
				{"name":"안전모","basePrice":1000,"status":"HIDDEN"}
				"""));
		patchJson("/api/v1/admin/products/" + productId + "/skus/999999", """
				{"optionName":"흰색","price":1000}
				""")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));
		patchJson("/api/v1/admin/products/" + productId, """
				{"name":"   "}
				""").andExpect(status().isBadRequest());
	}

	@Test
	void 잘못된_검색_조건은_500이_아니라_400을_돌려준다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/products").param("status", "DELETED")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/products").param("status", "WRONG")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/products").param("sort", "nope,asc")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/products/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/products/abc")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/products").param("sort", "nope,asc")).andExpect(status().isBadRequest());

		// 응답에 싣지 않는 컬럼으로는 정렬할 수 없다.
		mockMvc.perform(get("/api/v1/admin/users").param("sort", "passwordHash")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/orders").param("sort", "nope")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/inquiries").param("sort", "nope")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/quotes").param("sort", "nope")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/notices").param("sort", "title")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/notices").param("sort", "title")).andExpect(status().isBadRequest());

		// 허용된 기준은 그대로 동작한다.
		mockMvc.perform(get("/api/v1/admin/products").param("sort", "basePrice,desc")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/users").param("sort", "createdAt,desc")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/orders").param("sort", "totalAmount,desc")).andExpect(status().isOk());
	}

	// ───────── 주문 ─────────

	@Test
	void 주문을_조건별로_찾고_배송_단계를_순서대로_진행한다() throws Exception {
		long memberId = insertMember("buyer@example.com", "구매자", "USER");
		long paidId = insertOrder("ORD-0001", memberId, "PAID", 50000, LocalDateTime.now());
		insertOrder("ORD-0002", memberId, "PENDING", 10000, null);
		jdbcTemplate.update("""
				insert into order_item (order_id, sku_id, product_name, option_name, order_price, quantity, status)
				values (?, ?, '산업용 안전모', '흰색', 25000, 2, 'ORDERED')
				""", paidId, insertSku());
		jdbcTemplate.update("""
				insert into payment (order_id, payment_key, method, status, approved_amount, balance_amount,
				                     created_at, updated_at)
				values (?, 'pay-key-1', 'CARD', 'DONE', 50000, 50000, now(), now())
				""", paidId);

		mockMvc.perform(get("/api/v1/admin/orders")).andExpect(jsonPath("$.data.totalElements").value(2));
		mockMvc.perform(get("/api/v1/admin/orders").param("status", "PAID"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].orderNo").value("ORD-0001"));
		mockMvc.perform(get("/api/v1/admin/orders").param("orderNo", "0002"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/v1/admin/orders").param("memberId", String.valueOf(memberId)))
				.andExpect(jsonPath("$.data.totalElements").value(2));
		mockMvc.perform(get("/api/v1/admin/orders").param("memberId", "999999"))
				.andExpect(jsonPath("$.data.totalElements").value(0));

		mockMvc.perform(get("/api/v1/admin/orders/{id}", paidId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalAmount").value(50000))
				.andExpect(jsonPath("$.data.shippingAddress.recipient").value("홍길동"))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].productName").value("산업용 안전모"))
				.andExpect(jsonPath("$.data.items[0].quantity").value(2))
				.andExpect(jsonPath("$.data.payment.method").value("CARD"))
				.andExpect(content().string(not(containsString("pay-key-1"))));

		patchJson("/api/v1/admin/orders/" + paidId, """
				{"status":"PREPARING"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PREPARING"));
		patchJson("/api/v1/admin/orders/" + paidId, """
				{"status":"SHIPPED","courier":"한진택배","trackingNumber":"1234567890"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SHIPPED"))
				.andExpect(jsonPath("$.data.courier").value("한진택배"))
				.andExpect(jsonPath("$.data.trackingNumber").value("1234567890"));
		patchJson("/api/v1/admin/orders/" + paidId, """
				{"status":"DELIVERED"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DELIVERED"));
		mockMvc.perform(get("/api/v1/admin/orders").param("status", "DELIVERED"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void 허용되지_않는_주문_상태_변경과_송장_등록은_409로_거부한다() throws Exception {
		long memberId = insertMember("buyer@example.com", "구매자", "USER");
		long paidId = insertOrder("ORD-0001", memberId, "PAID", 50000, LocalDateTime.now());
		long pendingId = insertOrder("ORD-0002", memberId, "PENDING", 10000, null);

		// 단계 건너뛰기
		patchJson("/api/v1/admin/orders/" + paidId, """
				{"status":"SHIPPED"}
				""").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
		// 취소·결제완료는 관리자가 직접 지정할 수 없다
		patchJson("/api/v1/admin/orders/" + paidId, """
				{"status":"CANCELED"}
				""").andExpect(status().isConflict());
		patchJson("/api/v1/admin/orders/" + pendingId, """
				{"status":"PAID"}
				""").andExpect(status().isConflict());
		// 결제 전 주문
		patchJson("/api/v1/admin/orders/" + pendingId, """
				{"status":"PREPARING"}
				""").andExpect(status().isConflict());
		patchJson("/api/v1/admin/orders/" + pendingId, """
				{"trackingNumber":"1234567890"}
				""").andExpect(status().isConflict());

		mockMvc.perform(get("/api/v1/admin/orders/{id}", paidId)).andExpect(jsonPath("$.data.status").value("PAID"));
		mockMvc.perform(get("/api/v1/admin/orders/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
	}

	// ───────── 고객지원 ─────────

	@Test
	void 공지를_비공개로_등록했다가_공개로_바꾸고_삭제한다() throws Exception {
		long noticeId = idOf(postJson("/api/v1/admin/notices", """
				{"title":"시스템 점검 안내","content":"점검 내용","visible":false}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.pinned").value(false))
				.andExpect(jsonPath("$.data.visible").value(false)));

		mockMvc.perform(get("/api/v1/admin/notices"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].content").value("점검 내용"));
		mockMvc.perform(get("/api/v1/notices")).andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/v1/notices/{id}", noticeId)).andExpect(status().isNotFound());

		patchJson("/api/v1/admin/notices/" + noticeId, """
				{"visible":true,"pinned":true}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("시스템 점검 안내"))
				.andExpect(jsonPath("$.data.visible").value(true));
		mockMvc.perform(get("/api/v1/notices"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].pinned").value(true));

		postJson("/api/v1/admin/notices", """
				{"title":"제목만"}
				""").andExpect(status().isBadRequest());

		mockMvc.perform(delete("/api/v1/admin/notices/{id}", noticeId)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/notices")).andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(delete("/api/v1/admin/notices/{id}", noticeId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
	}

	@Test
	void FAQ를_등록_수정_삭제하면_고객용_목록에_반영된다() throws Exception {
		long faqId = idOf(postJson("/api/v1/admin/faqs", """
				{"category":"배송","question":"언제 도착하나요?","answer":"2~3일 걸립니다.","sortOrder":2}
				""").andExpect(status().isCreated()));
		mockMvc.perform(get("/api/v1/faqs"))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].sortOrder").value(2));

		patchJson("/api/v1/admin/faqs/" + faqId, """
				{"answer":"3~5일 걸립니다."}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.question").value("언제 도착하나요?"))
				.andExpect(jsonPath("$.data.answer").value("3~5일 걸립니다."));
		patchJson("/api/v1/admin/faqs/" + faqId, """
				{"question":"   "}
				""").andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/faqs")).andExpect(jsonPath("$.data[0].answer").value("3~5일 걸립니다."));

		mockMvc.perform(delete("/api/v1/admin/faqs/{id}", faqId)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/faqs")).andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(delete("/api/v1/admin/faqs/{id}", faqId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("FAQ_NOT_FOUND"));
	}

	@Test
	void 문의에_답변하면_답변완료로_바뀐다() throws Exception {
		jdbcTemplate.update("""
				insert into inquiry (member_id, contact_email, inquiry_type, title, content, status,
				                     created_at, updated_at)
				values (null, 'guest@example.com', '배송', '배송 문의', '언제 오나요?', 'RECEIVED', now(), now())
				""");
		Long inquiryId = jdbcTemplate.queryForObject("select id from inquiry where title = '배송 문의'", Long.class);

		mockMvc.perform(get("/api/v1/admin/inquiries").param("status", "RECEIVED"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].title").value("배송 문의"))
				.andExpect(content().string(not(containsString("guest@example.com"))));
		mockMvc.perform(get("/api/v1/admin/inquiries/{id}", inquiryId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.contactEmail").value("guest@example.com"))
				.andExpect(jsonPath("$.data.content").value("언제 오나요?"));

		patchJson("/api/v1/admin/inquiries/" + inquiryId, """
				{"answer":""}
				""").andExpect(status().isBadRequest());
		patchJson("/api/v1/admin/inquiries/" + inquiryId, """
				{"answer":"내일 도착 예정입니다."}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ANSWERED"))
				.andExpect(jsonPath("$.data.answer").value("내일 도착 예정입니다."));

		mockMvc.perform(get("/api/v1/admin/inquiries").param("status", "RECEIVED"))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/v1/admin/inquiries").param("status", "ANSWERED"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/v1/admin/inquiries")).andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/v1/admin/inquiries/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
	}

	// ───────── 견적 ─────────

	@Test
	void 견적_문의를_상태별로_조회하고_처리_상태를_바꾼다() throws Exception {
		long quoteId = insertQuote("이음건설");

		mockMvc.perform(get("/api/v1/admin/quotes").param("status", "RECEIVED"))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].companyName").value("이음건설"))
				.andExpect(jsonPath("$.data.content[0].phone").value("0212345678"));

		patchJson("/api/v1/admin/quotes/" + quoteId, """
				{"status":"IN_PROGRESS"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
		mockMvc.perform(get("/api/v1/admin/quotes").param("status", "RECEIVED"))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/v1/admin/quotes")).andExpect(jsonPath("$.data.totalElements").value(1));

		patchJson("/api/v1/admin/quotes/" + quoteId, "{}").andExpect(status().isBadRequest());
		patchJson("/api/v1/admin/quotes/" + quoteId, """
				{"status":"WRONG"}
				""").andExpect(status().isBadRequest());
		patchJson("/api/v1/admin/quotes/999999", """
				{"status":"COMPLETED"}
				""").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
	}

	// ───────── 배너 ─────────

	@Test
	void 배너를_등록_수정_삭제한다() throws Exception {
		long secondId = idOf(postJson("/api/v1/admin/banners", """
				{"imageUrl":"/files/second.png","sortOrder":2}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.data.visible").value(true)));
		long firstId = idOf(postJson("/api/v1/admin/banners", """
				{"imageUrl":"https://cdn.example.com/first.png","linkUrl":"/products/1","sortOrder":1,"visible":false}
				""").andExpect(status().isCreated()));

		mockMvc.perform(get("/api/v1/admin/banners"))
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].id").value(firstId))
				.andExpect(jsonPath("$.data[0].visible").value(false));

		patchJson("/api/v1/admin/banners/" + firstId, """
				{"linkUrl":"","visible":true}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/first.png"))
				.andExpect(jsonPath("$.data.linkUrl").doesNotExist())
				.andExpect(jsonPath("$.data.visible").value(true));

		postJson("/api/v1/admin/banners", """
				{"imageUrl":"/files/x.png","linkUrl":"javascript:alert(1)"}
				""").andExpect(status().isBadRequest());
		postJson("/api/v1/admin/banners", """
				{"linkUrl":"/products/1"}
				""").andExpect(status().isBadRequest());

		mockMvc.perform(delete("/api/v1/admin/banners/{id}", secondId)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/banners")).andExpect(jsonPath("$.data.length()").value(1));
		mockMvc.perform(delete("/api/v1/admin/banners/{id}", secondId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
	}

	// ───────── 파일 업로드 ─────────

	@Test
	void 이미지를_올리면_서버가_정한_이름으로_저장하고_누구나_내려받을_수_있다() throws Exception {
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4 };
		MockMultipartFile file = new MockMultipartFile("file", "../../evil.jsp", "text/plain", png);

		String body = mockMvc.perform(multipart("/api/v1/uploads").file(file))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(body, "$.data.url");

		// 클라이언트가 보낸 파일명·확장자가 아니라 내용으로 판별한 확장자가 붙는다.
		assertThat(url).matches("/files/[0-9a-f-]{36}\\.png");
		var response = mockMvc.perform(get(url).with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(png))
				.andReturn().getResponse();
		assertThat(response.getHeader("Cache-Control"))
				.contains("public", "max-age=31536000", "immutable");
	}

	@Test
	void 이미지가_아닌_파일은_확장자를_속여도_거부한다() throws Exception {
		MockMultipartFile fake = new MockMultipartFile("file", "photo.png", "image/png",
				"<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
		MockMultipartFile empty = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

		mockMvc.perform(multipart("/api/v1/uploads").file(fake))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_FILE"));
		mockMvc.perform(multipart("/api/v1/uploads").file(empty))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_FILE"));
		mockMvc.perform(multipart("/api/v1/uploads"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mockMvc.perform(post("/api/v1/uploads").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnsupportedMediaType());
	}

	// ───────── 대시보드 ─────────

	@Test
	void 대시보드는_기간_내_결제_주문과_신규_회원_그리고_대기_중인_문의를_집계한다() throws Exception {
		long memberId = insertMember("buyer@example.com", "구매자", "USER");
		insertMember("admin@example.com", "관리자", "ADMIN");
		insertOrder("ORD-TODAY", memberId, "PAID", 50000, LocalDateTime.now());
		insertOrder("ORD-OLD", memberId, "DELIVERED", 30000, LocalDateTime.now().minusDays(10));
		insertOrder("ORD-CANCELED", memberId, "CANCELED", 70000, LocalDateTime.now());
		insertOrder("ORD-PENDING", memberId, "PENDING", 90000, null);
		insertQuote("이음건설");

		mockMvc.perform(get("/api/v1/admin/dashboard").param("period", "today"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.period").value("TODAY"))
				.andExpect(jsonPath("$.data.paidOrderCount").value(1))
				.andExpect(jsonPath("$.data.salesAmount").value(50000))
				.andExpect(jsonPath("$.data.newMemberCount").value(1))
				.andExpect(jsonPath("$.data.pendingInquiryCount").value(0))
				.andExpect(jsonPath("$.data.pendingQuoteCount").value(1));
		mockMvc.perform(get("/api/v1/admin/dashboard").param("period", "MONTH"))
				.andExpect(jsonPath("$.data.paidOrderCount").value(2))
				.andExpect(jsonPath("$.data.salesAmount").value(80000));
		mockMvc.perform(get("/api/v1/admin/dashboard"))
				.andExpect(jsonPath("$.data.period").value("WEEK"))
				.andExpect(jsonPath("$.data.paidOrderCount").value(1));
		mockMvc.perform(get("/api/v1/admin/dashboard").param("period", "year"))
				.andExpect(status().isBadRequest());
	}

	// ───────── 도우미 ─────────

	private ResultActions postJson(String url, String body) throws Exception {
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions patchJson(String url, String body) throws Exception {
		return mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String stockInUrl(long productId, long skuId) {
		return "/api/v1/admin/products/" + productId + "/skus/" + skuId + "/stock-in";
	}

	private long idOf(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.data.id")).longValue();
	}

	private ResultActions adminLogin(String email, String password) throws Exception {
		return postJson("/api/v1/admin/auth/login", """
				{"email":"%s","password":"%s"}
				""".formatted(email, password));
	}

	private long insertMember(String email, String name, String role) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, phone, role, status, created_at, updated_at)
				values (?, ?, ?, '01012345678', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD), name, role);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}

	private String roleOf(String email) {
		return jdbcTemplate.queryForObject("select role from member where email = ?", String.class, email);
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor admin(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor superAdmin(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
						new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
	}

	private long insertOrder(String orderNo, long memberId, String status, long totalAmount, LocalDateTime paidAt) {
		jdbcTemplate.update("""
				insert into orders (order_no, member_id, status, total_amount, item_amount, discount_amount,
				                    shipping_fee, order_name, recipient, expires_at, paid_at, version,
				                    created_at, updated_at)
				values (?, ?, ?, ?, ?, 0, 0, '산업용 안전모 외 1건', '홍길동', now(), ?, 0, now(), now())
				""", orderNo, memberId, status, totalAmount, totalAmount, paidAt);
		return jdbcTemplate.queryForObject("select id from orders where order_no = ?", Long.class, orderNo);
	}

	/** 주문 품목은 실제로 있는 옵션만 가리킬 수 있으므로(FK 제약) 옵션 하나를 만들어 그 ID를 돌려준다. */
	private long insertSku() {
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values ('주문된 상품', 25000, true, 'ON_SALE', now(), now())
				""");
		Long productId = jdbcTemplate.queryForObject("select id from product where name = '주문된 상품'", Long.class);
		jdbcTemplate.update("""
				insert into product_sku (product_id, option_name, price, stock, version) values (?, '흰색', 25000, 10, 0)
				""", productId);
		return jdbcTemplate.queryForObject("select id from product_sku where product_id = ?", Long.class, productId);
	}

	private long insertQuote(String companyName) {
		jdbcTemplate.update("""
				insert into quote_inquiry (company_name, manager_name, phone, content, status, created_at, updated_at)
				values (?, '김담당', '0212345678', '안전 솔루션 견적 요청', 'RECEIVED', now(), now())
				""", companyName);
		return jdbcTemplate.queryForObject("select id from quote_inquiry where company_name = ?", Long.class,
				companyName);
	}
}
