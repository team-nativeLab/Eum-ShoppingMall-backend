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

import com.example.shopping.global.privacy.Masking;
import com.jayway.jsonpath.JsonPath;

/** 상품 상세의 상품 문의(Q&A)와 관리자 답변 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductQuestionApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 회원이_남긴_문의를_누구나_보고_작성자_이름은_가려진다() throws Exception {
		long productId = insertProduct("안전모", "ON_SALE");
		long writerId = insertMember("writer@example.com", "홍길동");

		ask(productId, member(writerId), "사이즈 문의", "머리 둘레 60cm도 맞나요?", false)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("WAITING"))
				.andExpect(jsonPath("$.data.isMine").value(true));

		mockMvc.perform(get("/api/v1/products/{id}/questions", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].authorName").value("홍*동"))
				.andExpect(jsonPath("$.data.content[0].title").value("사이즈 문의"))
				.andExpect(jsonPath("$.data.content[0].content").value("머리 둘레 60cm도 맞나요?"))
				.andExpect(jsonPath("$.data.content[0].isMine").value(false));
		// 비회원은 문의를 남길 수 없다.
		mockMvc.perform(post("/api/v1/products/{id}/questions", productId).contentType(MediaType.APPLICATION_JSON)
						.content(question("제목", "내용", false)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 비밀_문의는_작성자_본인과_관리자만_내용과_답변을_본다() throws Exception {
		long productId = insertProduct("보안경", "ON_SALE");
		long writerId = insertMember("secret@example.com", "김비밀");
		long otherId = insertMember("other@example.com", "이다른");
		long adminId = insertMember("admin@example.com", "관리자");
		long questionId = idOf(ask(productId, member(writerId), "가격 문의", "대량 구매 할인 되나요?", true));

		mockMvc.perform(patch("/api/v1/admin/product-questions/{id}", questionId).with(admin(adminId))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"answer\":\"견적 문의로 남겨 주세요.\",\"adminMemo\":\"영업팀 전달\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ANSWERED"))
				.andExpect(jsonPath("$.data.answeredBy").value(adminId));

		// 다른 회원·비회원에게는 제목을 바꾸고 내용·답변을 비운다.
		for (RequestPostProcessor viewer : new RequestPostProcessor[] { member(otherId), request -> request }) {
			mockMvc.perform(get("/api/v1/products/{id}/questions", productId).with(viewer))
					.andExpect(jsonPath("$.data.content[0].title").value("비밀 문의입니다."))
					.andExpect(jsonPath("$.data.content[0].content").doesNotExist())
					.andExpect(jsonPath("$.data.content[0].answer").doesNotExist())
					.andExpect(jsonPath("$.data.content[0].isPrivate").value(true))
					.andExpect(jsonPath("$.data.content[0].status").value("ANSWERED"));
		}
		// 작성자는 내용과 답변을 본다. 관리자 메모는 고객에게 보이지 않는다.
		String body = mockMvc.perform(get("/api/v1/products/{id}/questions", productId).with(member(writerId)))
				.andExpect(jsonPath("$.data.content[0].content").value("대량 구매 할인 되나요?"))
				.andExpect(jsonPath("$.data.content[0].answer").value("견적 문의로 남겨 주세요."))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("영업팀 전달");

		// 관리자는 비밀 문의도 작성자와 함께 본다.
		mockMvc.perform(get("/api/v1/admin/product-questions").with(admin(adminId)).param("status", "ANSWERED")
						.param("productId", String.valueOf(productId)))
				.andExpect(jsonPath("$.data.content[0].authorName").value("김비밀"))
				.andExpect(jsonPath("$.data.content[0].content").value("대량 구매 할인 되나요?"))
				.andExpect(jsonPath("$.data.content[0].adminMemo").value("영업팀 전달"))
				.andExpect(jsonPath("$.data.content[0].productName").value("보안경"));
	}

	@Test
	void 빈_답변을_남기면_답변_대기로_돌아가고_조회는_감사_로그에_남는다() throws Exception {
		long productId = insertProduct("안전화", "ON_SALE");
		long writerId = insertMember("cancel@example.com", "박취소");
		long adminId = insertMember("admin2@example.com", "관리자");
		long questionId = idOf(ask(productId, member(writerId), "재입고", "언제 들어오나요?", false));

		answer(adminId, questionId, "다음 주 입고 예정입니다.").andExpect(jsonPath("$.data.status").value("ANSWERED"));
		answer(adminId, questionId, "")
				.andExpect(jsonPath("$.data.status").value("WAITING"))
				.andExpect(jsonPath("$.data.answer").doesNotExist());
		mockMvc.perform(get("/api/v1/admin/product-questions/{id}", questionId).with(admin(adminId)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/admin/product-questions/{id}", 999_999L).with(admin(adminId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_QUESTION_NOT_FOUND"));

		assertThat(jdbcTemplate.queryForList("""
				select concat(action, ' ', target_type) from admin_audit_log where actor_member_id = ? order by id
				""", String.class, adminId))
				.contains("PATCH product-questions", "GET product-questions");
	}

	@Test
	void 숨긴_상품에는_문의를_보거나_남길_수_없고_일반_회원은_관리자_API를_쓸_수_없다() throws Exception {
		long hiddenId = insertProduct("숨긴 상품", "HIDDEN");
		long memberId = insertMember("hidden@example.com", "최숨김");

		mockMvc.perform(get("/api/v1/products/{id}/questions", hiddenId)).andExpect(status().isNotFound());
		ask(hiddenId, member(memberId), "제목", "내용", false)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
		ask(insertProduct("길이 확인", "ON_SALE"), member(memberId), "", "내용", false)
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/product-questions").with(member(memberId)))
				.andExpect(status().isForbidden());
	}

	@Test
	void 이름은_첫_글자와_마지막_글자만_남긴다() {
		assertThat(Masking.name("홍길동")).isEqualTo("홍*동");
		assertThat(Masking.name("남궁민수")).isEqualTo("남**수");
		assertThat(Masking.name("홍길")).isEqualTo("홍*");
		assertThat(Masking.name("홍")).isEqualTo("*");
		assertThat(Masking.name(" ")).isEqualTo("*");
	}

	private ResultActions ask(long productId, RequestPostProcessor member, String title, String content,
			boolean isPrivate) throws Exception {
		return mockMvc.perform(post("/api/v1/products/{id}/questions", productId).with(member)
				.contentType(MediaType.APPLICATION_JSON).content(question(title, content, isPrivate)));
	}

	private ResultActions answer(long adminId, long questionId, String answer) throws Exception {
		return mockMvc.perform(patch("/api/v1/admin/product-questions/{id}", questionId).with(admin(adminId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"" + answer + "\"}"));
	}

	private static String question(String title, String content, boolean isPrivate) {
		return """
				{"title":"%s","content":"%s","isPrivate":%s}
				""".formatted(title, content, isPrivate);
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

	private long insertProduct(String name, String status) {
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values (?, 10000, true, ?, now(), now())
				""", name, status);
		return jdbcTemplate.queryForObject("select id from product where name = ?", Long.class, name);
	}

	private long insertMember(String email, String name) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, 'hash', ?, 'USER', 'ACTIVE', now(), now())
				""", email, name);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}
}
