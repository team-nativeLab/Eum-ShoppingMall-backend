package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** 상품 리뷰: 작성 자격, 목록·요약, 상품 평점, 관리자 숨기기·답변 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductReviewApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 배송이_끝난_주문의_상품만_품목마다_한_번_리뷰를_쓰고_평점이_상품에_반영된다() throws Exception {
		long buyerId = insertMember("buyer@example.com", "홍길동");
		long productId = insertProduct("경량 안전화");
		long skuId = insertSku(productId, "260mm");
		long deliveredItem = insertOrderItem(insertOrder("ORD-R-1", buyerId, "DELIVERED"), skuId, "ORDERED");
		long shippingItem = insertOrderItem(insertOrder("ORD-R-2", buyerId, "SHIPPED"), skuId, "ORDERED");

		// 리뷰를 쓸 수 있는 품목은 배송이 끝난 것뿐이다.
		mockMvc.perform(get("/api/v1/me/reviewable-items").with(member(buyerId)))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].orderItemId").value(deliveredItem))
				.andExpect(jsonPath("$.data[0].orderNo").value("ORD-R-1"))
				.andExpect(jsonPath("$.data[0].productId").value(productId))
				.andExpect(jsonPath("$.data[0].optionLabel").value("260mm"));

		write(productId, buyerId, shippingItem, 5)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
		write(productId, buyerId, deliveredItem, 4)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.authorName").value("홍*동"))
				.andExpect(jsonPath("$.data.optionLabel").value("260mm"))
				.andExpect(jsonPath("$.data.isMine").value(true));
		write(productId, buyerId, deliveredItem, 5)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("REVIEW_ALREADY_WRITTEN"));

		// 다 쓴 품목은 목록에서 빠지고, 상품 상세·목록에 평점이 보인다.
		mockMvc.perform(get("/api/v1/me/reviewable-items").with(member(buyerId)))
				.andExpect(jsonPath("$.data.length()").value(0));
		entityManager.clear();
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(jsonPath("$.data.rating").value(4.0))
				.andExpect(jsonPath("$.data.reviewCount").value(1));
		mockMvc.perform(get("/api/v1/products").param("query", "경량 안전화"))
				.andExpect(jsonPath("$.data.content[0].rating").value(4.0))
				.andExpect(jsonPath("$.data.content[0].reviewCount").value(1));
	}

	@Test
	void 남의_주문이나_다른_상품의_품목으로는_리뷰를_쓸_수_없다() throws Exception {
		long buyerId = insertMember("owner@example.com", "김주인");
		long otherId = insertMember("stranger@example.com", "이남남");
		long productId = insertProduct("보안경");
		long otherProductId = insertProduct("귀마개");
		long item = insertOrderItem(insertOrder("ORD-R-3", buyerId, "DELIVERED"), insertSku(productId, "기본"),
				"ORDERED");
		long canceledItem = insertOrderItem(insertOrder("ORD-R-4", buyerId, "DELIVERED"),
				insertSku(productId, "흑색"), "CANCELED");

		write(productId, otherId, item, 5).andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
		write(otherProductId, buyerId, item, 5).andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
		write(productId, buyerId, canceledItem, 5).andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
		mockMvc.perform(post("/api/v1/products/{id}/reviews", productId).contentType(MediaType.APPLICATION_JSON)
						.content(review(item, 5)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/products/{id}/reviews", productId).with(member(buyerId))
						.contentType(MediaType.APPLICATION_JSON).content(review(item, 6)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 요약은_별점별_수와_평균을_주고_관리자가_숨기면_목록과_평점에서_빠진다() throws Exception {
		long productId = insertProduct("안전모");
		long adminId = insertMember("admin@example.com", "관리자");
		long[] reviewIds = new long[3];
		int[] ratings = { 5, 4, 1 };
		for (int i = 0; i < 3; i++) {
			long buyerId = insertMember("buyer" + i + "@example.com", "구매자" + i);
			long item = insertOrderItem(insertOrder("ORD-S-" + i, buyerId, "DELIVERED"),
					insertSku(productId, "옵션" + i), "ORDERED");
			reviewIds[i] = idOf(write(productId, buyerId, item, ratings[i]));
		}

		mockMvc.perform(get("/api/v1/products/{id}/reviews/summary", productId))
				.andExpect(jsonPath("$.data.totalCount").value(3))
				.andExpect(jsonPath("$.data.averageRating").value(3.3))
				.andExpect(jsonPath("$.data.ratingCounts['5']").value(1))
				.andExpect(jsonPath("$.data.ratingCounts['3']").value(0))
				.andExpect(jsonPath("$.data.ratingCounts['1']").value(1));
		mockMvc.perform(get("/api/v1/products/{id}/reviews", productId).param("sort", "rating_low"))
				.andExpect(jsonPath("$.data.content[0].rating").value(1))
				.andExpect(jsonPath("$.data.content[2].rating").value(5));

		// 별점 1점 리뷰를 숨기고 답변을 단다. 메모는 고객에게 보이지 않는다.
		mockMvc.perform(patch("/api/v1/admin/reviews/{id}", reviewIds[2]).with(admin(adminId))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"visible\":false,\"adminMemo\":\"욕설 신고\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.visible").value(false));
		mockMvc.perform(patch("/api/v1/admin/reviews/{id}", reviewIds[0]).with(admin(adminId))
						.contentType(MediaType.APPLICATION_JSON).content("{\"adminReply\":\"감사합니다.\"}"))
				.andExpect(jsonPath("$.data.adminReply").value("감사합니다."));

		String body = mockMvc.perform(get("/api/v1/products/{id}/reviews", productId))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[1].adminReply").value("감사합니다."))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("욕설 신고");
		entityManager.clear();
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(jsonPath("$.data.rating").value(4.5))
				.andExpect(jsonPath("$.data.reviewCount").value(2));

		// 다시 보이면 평점에 다시 들어간다. 같은 값을 다시 보내도 두 번 세지 않는다.
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(patch("/api/v1/admin/reviews/{id}", reviewIds[2]).with(admin(adminId))
					.contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true}"));
		}
		entityManager.clear();
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(jsonPath("$.data.reviewCount").value(3));
		mockMvc.perform(get("/api/v1/admin/reviews").with(admin(adminId)).param("productId", String.valueOf(productId))
						.param("rating", "1"))
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].authorName").value("구매자2"))
				.andExpect(jsonPath("$.data.content[0].adminMemo").value("욕설 신고"));
	}

	private ResultActions write(long productId, long memberId, long orderItemId, int rating) throws Exception {
		return mockMvc.perform(post("/api/v1/products/{id}/reviews", productId).with(member(memberId))
				.contentType(MediaType.APPLICATION_JSON).content(review(orderItemId, rating)));
	}

	private static String review(long orderItemId, int rating) {
		return """
				{"orderItemId":%d,"rating":%d,"content":"현장에서 잘 쓰고 있습니다."}
				""".formatted(orderItemId, rating);
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}

	private RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private RequestPostProcessor admin(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private long insertMember(String email, String name) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, 'hash', ?, 'USER', 'ACTIVE', now(), now())
				""", email, name);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}

	private long insertProduct(String name) {
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values (?, 30000, true, 'ON_SALE', now(), now())
				""", name);
		return jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
	}

	private long insertSku(long productId, String optionName) {
		jdbcTemplate.update("""
				insert into product_sku (product_id, option_name, price, stock, version) values (?, ?, 30000, 10, 0)
				""", productId, optionName);
		return jdbcTemplate.queryForObject("select id from product_sku where product_id = ? and option_name = ?",
				Long.class, productId, optionName);
	}

	private long insertOrder(String orderNo, long memberId, String status) {
		jdbcTemplate.update("""
				insert into orders (order_no, member_id, status, total_amount, item_amount, discount_amount,
				                    shipping_fee, order_name, recipient, expires_at, version, created_at, updated_at)
				values (?, ?, ?, 30000, 30000, 0, 0, '안전용품', '홍길동', now(), 0, now(), now())
				""", orderNo, memberId, status);
		return jdbcTemplate.queryForObject("select id from orders where order_no = ?", Long.class, orderNo);
	}

	private long insertOrderItem(long orderId, long skuId, String status) {
		jdbcTemplate.update("""
				insert into order_item (order_id, sku_id, product_name, option_name, order_price, quantity, status)
				select ?, s.id, p.name, s.option_name, 30000, 1, ? from product_sku s join product p on p.id = s.product_id
				where s.id = ?
				""", orderId, status, skuId);
		return jdbcTemplate.queryForObject("select max(id) from order_item where order_id = ?", Long.class, orderId);
	}
}
