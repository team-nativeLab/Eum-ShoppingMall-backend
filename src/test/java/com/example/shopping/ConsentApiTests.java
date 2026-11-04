package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

/** 회원가입·비회원 문의 때 받는 동의(#21) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsentApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 필수_동의_없이는_가입할_수_없다() throws Exception {
		signup("""
				{"email":"consent-1@example.com","password":"test-pass-1234","name":"홍길동","privacyAgreed":true}
				""").andExpect(status().isBadRequest());
		signup("""
				{"email":"consent-1@example.com","password":"test-pass-1234","name":"홍길동",
				 "termsAgreed":true,"privacyAgreed":false}
				""").andExpect(status().isBadRequest());

		assertThat(jdbcTemplate.queryForObject("select count(*) from member where email = ?", Long.class,
				"consent-1@example.com")).isZero();
	}

	@Test
	void 가입하면_항목별_동의_여부와_그때의_약관_버전을_남긴다() throws Exception {
		LocalDateTime termsUpdatedAt = LocalDateTime.of(2026, 11, 1, 9, 0);
		jdbcTemplate.update("""
				insert into site_content (code, title, body, created_at, updated_at) values ('terms', '이용약관', '본문', ?, ?)
				""", termsUpdatedAt, termsUpdatedAt);

		signup("""
				{"email":"consent-2@example.com","password":"test-pass-1234","name":"홍길동",
				 "termsAgreed":true,"privacyAgreed":true}
				""").andExpect(status().is2xxSuccessful());

		List<Map<String, Object>> consents = jdbcTemplate.queryForList("""
				select c.consent_type, c.agreed, c.document_version from member_consent c
				join member m on m.id = c.member_id where m.email = ? order by c.id
				""", "consent-2@example.com");
		assertThat(consents).extracting(row -> row.get("CONSENT_TYPE"), row -> row.get("AGREED"),
				row -> row.get("DOCUMENT_VERSION"))
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple("TERMS", true, termsUpdatedAt.toString()),
						// 개인정보 처리방침이 아직 등록되지 않았으면 버전은 비워 둔다.
						org.assertj.core.groups.Tuple.tuple("PRIVACY", true, null),
						// 마케팅 수신은 선택이고, 보내지 않으면 동의하지 않은 것으로 남긴다.
						org.assertj.core.groups.Tuple.tuple("MARKETING", false, null));
	}

	@Test
	void 비회원은_개인정보_수집에_동의해야_문의와_견적을_접수할_수_있다() throws Exception {
		postJson("/api/v1/support/inquiries", inquiry(null), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PRIVACY_CONSENT_REQUIRED"));
		postJson("/api/v1/quotes", quote(false), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PRIVACY_CONSENT_REQUIRED"));

		postJson("/api/v1/support/inquiries", inquiry(true), null).andExpect(status().isCreated());
		postJson("/api/v1/quotes", quote(true), null).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from inquiry where member_id is null and privacy_agreed_at is not null", Long.class))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from quote_inquiry where member_id is null and privacy_agreed_at is not null",
				Long.class)).isEqualTo(1);
	}

	@Test
	void 회원은_가입할_때_동의했으므로_문의에_동의를_다시_보내지_않아도_된다() throws Exception {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values ('consent-member@example.com', 'x', '회원', 'USER', 'ACTIVE', now(), now())
				""");
		long memberId = jdbcTemplate.queryForObject("select id from member where email = 'consent-member@example.com'",
				Long.class);

		postJson("/api/v1/support/inquiries", inquiry(null), member(memberId)).andExpect(status().isCreated());
	}

	private String inquiry(Boolean privacyAgreed) {
		return """
				{"inquiryType":"주문","title":"배송 문의","content":"언제 오나요?","contactEmail":"guest@example.com"%s}
				""".formatted(privacyAgreed == null ? "" : ",\"privacyAgreed\":" + privacyAgreed);
	}

	private String quote(boolean privacyAgreed) {
		return """
				{"companyName":"이음건설","managerName":"홍길동","phone":"010-1234-5678","content":"안전모 100개",
				 "privacyAgreed":%s}
				""".formatted(privacyAgreed);
	}

	private ResultActions signup(String body) throws Exception {
		return postJson("/api/v1/auth/signup", body, null);
	}

	private ResultActions postJson(String uri, String body, RequestPostProcessor auth) throws Exception {
		var request = post(uri).contentType(MediaType.APPLICATION_JSON).content(body);
		return mockMvc.perform(auth == null ? request : request.with(auth));
	}

	private RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}
}
