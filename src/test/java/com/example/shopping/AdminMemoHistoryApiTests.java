package com.example.shopping;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.order.service.OrderTxService;
import com.jayway.jsonpath.JsonPath;

/** 관리자 내부 메모와 상태 변경 이력(#223) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminMemoHistoryApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OrderTxService orderTxService;

	@Test
	void 주문의_관리자_상태_변경을_이력에_남기고_내부_메모는_관리자만_본다() throws Exception {
		long adminId = insertMember("admin@example.com", "관리자");
		long buyerId = insertMember("buyer@example.com", "구매자");
		long orderId = insertOrder("ORD-0001", buyerId, "PAID", LocalDateTime.now());
		String path = "/api/v1/admin/orders/" + orderId;

		patchJson(path, admin(adminId), "{\"status\":\"PREPARING\",\"adminMemo\":\" 부재 시 경비실에 맡김 \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.adminMemo").value("부재 시 경비실에 맡김"))
				.andExpect(jsonPath("$.data.statusHistory.length()").value(1))
				.andExpect(jsonPath("$.data.statusHistory[0].status").value("PREPARING"))
				.andExpect(jsonPath("$.data.statusHistory[0].note").value("관리자 상태 변경"));

		// 택배사·송장번호 없이 배송중으로 바꿀 수 없다. 같은 요청에 보내면 된다.
		patchJson(path, admin(adminId), "{\"status\":\"SHIPPED\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVOICE_REQUIRED"));
		patchJson(path, admin(adminId), """
				{"status":"SHIPPED","courier":"CJ대한통운","trackingNumber":"1234567890"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SHIPPED"))
				.andExpect(jsonPath("$.data.statusHistory.length()").value(2))
				.andExpect(jsonPath("$.data.statusHistory[1].status").value("SHIPPED"));
		// 이미 그 상태면 그대로 두고 이력도 늘리지 않는다.
		patchJson(path, admin(adminId), "{\"status\":\"SHIPPED\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusHistory.length()").value(2));

		mockMvc.perform(get("/api/v1/orders/{orderNo}", "ORD-0001").with(member(buyerId)))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("경비실"))))
				.andExpect(content().string(not(containsString("adminMemo"))));
		patchJson(path, admin(adminId), "{\"adminMemo\":\"\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.adminMemo").value(""))
				.andExpect(jsonPath("$.data.status").value("SHIPPED"));
	}

	@Test
	void 결제_기한이_지나_만료된_주문도_이력에_남는다() throws Exception {
		long adminId = insertMember("admin@example.com", "관리자");
		long orderId = insertOrder("ORD-0002", insertMember("buyer@example.com", "구매자"), "PENDING", null);

		orderTxService.expire(orderId);

		mockMvc.perform(get("/api/v1/admin/orders/{id}", orderId).with(admin(adminId)))
				.andExpect(jsonPath("$.data.status").value("EXPIRED"))
				.andExpect(jsonPath("$.data.statusHistory[0].status").value("EXPIRED"))
				.andExpect(jsonPath("$.data.statusHistory[0].note").value("결제 기한 만료"));
	}

	@Test
	void 일대일_문의는_접수_답변_답변_취소를_이력에_남기고_메모만_고칠_수_있다() throws Exception {
		long adminId = insertMember("admin@example.com", "관리자");
		long inquiryId = idOf(mockMvc.perform(post("/api/v1/support/inquiries").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"inquiryType":"배송","title":"배송 문의","content":"언제 오나요?","contactEmail":"guest@example.com",
								 "privacyAgreed":true}
								"""))
				.andExpect(status().isCreated()));
		String path = "/api/v1/admin/inquiries/" + inquiryId;

		patchJson(path, admin(adminId), "{\"adminMemo\":\" 택배사 확인 중 \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RECEIVED"))
				.andExpect(jsonPath("$.data.adminMemo").value("택배사 확인 중"))
				.andExpect(jsonPath("$.data.statusHistory.length()").value(1))
				.andExpect(jsonPath("$.data.statusHistory[0].note").value("문의 접수"));
		patchJson(path, admin(adminId), "{\"answer\":\"내일 도착 예정입니다.\"}")
				.andExpect(jsonPath("$.data.status").value("ANSWERED"))
				.andExpect(jsonPath("$.data.adminMemo").value("택배사 확인 중"))
				.andExpect(jsonPath("$.data.statusHistory[1].status").value("ANSWERED"))
				.andExpect(jsonPath("$.data.statusHistory[1].note").value("답변 등록"));
		patchJson(path, admin(adminId), "{\"answer\":\"\"}")
				.andExpect(jsonPath("$.data.status").value("RECEIVED"))
				.andExpect(jsonPath("$.data.answer").doesNotExist())
				.andExpect(jsonPath("$.data.statusHistory.length()").value(3))
				.andExpect(jsonPath("$.data.statusHistory[2].note").value("답변 취소"));
		mockMvc.perform(get(path).with(admin(adminId)))
				.andExpect(jsonPath("$.data.statusHistory.length()").value(3));
	}

	@Test
	void 상품_문의는_등록과_답변을_이력에_남기고_목록에는_이력을_싣지_않는다() throws Exception {
		long adminId = insertMember("admin@example.com", "관리자");
		long writerId = insertMember("writer@example.com", "홍길동");
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values ('안전모', 10000, true, 'ON_SALE', now(), now())
				""");
		long productId = jdbcTemplate.queryForObject("select id from product where name = '안전모'", Long.class);
		long questionId = idOf(mockMvc.perform(post("/api/v1/products/{id}/questions", productId).with(member(writerId))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"사이즈 문의\",\"content\":\"60cm도 맞나요?\",\"isPrivate\":false}"))
				.andExpect(status().isCreated()));

		patchJson("/api/v1/admin/product-questions/" + questionId, admin(adminId), "{\"answer\":\"맞습니다.\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.statusHistory.length()").value(2))
				.andExpect(jsonPath("$.data.statusHistory[0].status").value("WAITING"))
				.andExpect(jsonPath("$.data.statusHistory[0].note").value("문의 등록"))
				.andExpect(jsonPath("$.data.statusHistory[1].status").value("ANSWERED"));
		mockMvc.perform(get("/api/v1/admin/product-questions").with(admin(adminId)))
				.andExpect(jsonPath("$.data.content[0].id").value(questionId))
				.andExpect(jsonPath("$.data.content[0].statusHistory").doesNotExist());
	}

	private ResultActions patchJson(String path, RequestPostProcessor user, String body) throws Exception {
		return mockMvc.perform(patch(path).with(user).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private long insertMember(String email, String name) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, phone, role, status, created_at, updated_at)
				values (?, 'x', ?, '01012345678', 'USER', 'ACTIVE', now(), now())
				""", email, name);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}

	private long insertOrder(String orderNo, long memberId, String status, LocalDateTime paidAt) {
		jdbcTemplate.update("""
				insert into orders (order_no, member_id, status, total_amount, item_amount, discount_amount,
				                    shipping_fee, order_name, recipient, expires_at, paid_at, version,
				                    created_at, updated_at)
				values (?, ?, ?, 50000, 50000, 0, 0, '산업용 안전모', '홍길동', now(), ?, 0, now(), now())
				""", orderNo, memberId, status, paidAt);
		return jdbcTemplate.queryForObject("select id from orders where order_no = ?", Long.class, orderNo);
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}

	private static RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private static RequestPostProcessor admin(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}
}
