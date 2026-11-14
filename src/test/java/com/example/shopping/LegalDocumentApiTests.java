package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

/** 약관·정책 문서 버전 관리와 고객 화면, 동의 기록의 약관 버전. 따로 표시하지 않은 테스트는 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class LegalDocumentApiTests {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 고객_화면은_시행일이_지난_공개_버전_중_가장_최근_것을_보여_준다() throws Exception {
		save("terms", document("1.0", "첫 약관", TODAY.minusDays(30), true)).andExpect(status().isOk());
		save("terms", document("2.0", "바뀔 약관", TODAY.plusDays(7), true)).andExpect(status().isOk());
		save("terms", document("3.0-draft", "작성 중", TODAY.minusDays(1), false)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/legal-documents/terms").with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("terms"))
				.andExpect(jsonPath("$.data.version").value("1.0"))
				.andExpect(jsonPath("$.data.body").value("첫 약관 본문"))
				.andExpect(jsonPath("$.data.effectiveAt").value(TODAY.minusDays(30).toString()));
		// 공개 버전 목록에는 시행 예정 버전도 있고, 공개하지 않은 버전은 없다.
		mockMvc.perform(get("/api/v1/legal-documents/terms/versions").with(anonymous()))
				.andExpect(jsonPath("$.data[*].version").value(contains("2.0", "1.0")))
				.andExpect(jsonPath("$.data[*].current").value(contains(false, true)));
		mockMvc.perform(get("/api/v1/legal-documents/terms").param("version", "2.0").with(anonymous()))
				.andExpect(jsonPath("$.data.title").value("바뀔 약관"));
		mockMvc.perform(get("/api/v1/legal-documents/terms").param("version", "3.0-draft").with(anonymous()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("LEGAL_DOCUMENT_NOT_FOUND"));

		// 관리자 목록은 종류별로 시행일이 가장 늦은 버전, 버전 목록은 공개하지 않은 버전까지
		mockMvc.perform(get("/api/v1/admin/legal-documents"))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].version").value("2.0"))
				.andExpect(jsonPath("$.data[0].locked").value(false));
		mockMvc.perform(get("/api/v1/admin/legal-documents/terms/versions"))
				.andExpect(jsonPath("$.data[*].version").value(contains("2.0", "3.0-draft", "1.0")))
				.andExpect(jsonPath("$.data[*].locked").value(contains(false, false, true)));
	}

	@Test
	void 시행_중인_공개_버전은_고칠_수_없고_시행_예정이나_비공개_버전은_고칠_수_있다() throws Exception {
		save("privacy", document("1.0", "처리방침", TODAY, true)).andExpect(status().isOk());
		save("privacy", document("1.1", "처리방침", TODAY.plusDays(7), true)).andExpect(status().isOk());

		save("privacy", document("1.0", "몰래 고친 처리방침", TODAY, true))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LEGAL_DOCUMENT_LOCKED"));
		save("privacy", document("1.1", "고친 처리방침", TODAY.plusDays(8), true))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("고친 처리방침"))
				.andExpect(jsonPath("$.data.effectiveAt").value(TODAY.plusDays(8).toString()));
		mockMvc.perform(get("/api/v1/legal-documents/privacy").with(anonymous()))
				.andExpect(jsonPath("$.data.title").value("처리방침"));
	}

	@Test
	void 없는_문서와_형식이_틀린_입력은_거절한다() throws Exception {
		mockMvc.perform(get("/api/v1/legal-documents/privacy").with(anonymous()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("LEGAL_DOCUMENT_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/legal-documents/unknown").with(anonymous()))
				.andExpect(status().isNotFound());
		save("unknown", document("1.0", "문서", TODAY, true)).andExpect(status().isNotFound());

		save("guide", document("1.0", "안내", TODAY, true).replace("안내 본문", "<script>alert(1)</script>"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		save("guide", document("1 0", "안내", TODAY, true)).andExpect(status().isBadRequest());
		save("guide", document("1.0", "안내", TODAY, true).replace(",\"effectiveAt\":\"" + TODAY + "\"", ""))
				.andExpect(status().isBadRequest());
		save("guide", document("1.0", "안내", TODAY, true).replace("\"" + TODAY + "\"", "\"2026-13-01\""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithAnonymousUser
	void 동의_기록에는_시행_중인_약관_문서의_버전을_남긴다() throws Exception {
		jdbcTemplate.update("""
				insert into legal_document (document_type, version, title, body, effective_at, published,
				                            created_at, updated_at) values
				('TERMS', '2.0', '이용약관', '본문', ?, true, now(), now()),
				('TERMS', '3.0', '이용약관', '본문', ?, true, now(), now()),
				('PRIVACY', '1.1', '처리방침', '본문', ?, true, now(), now())
				""", TODAY, TODAY.plusDays(7), TODAY.minusDays(10));

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"legal-consent@example.com","password":"test-pass-1234","name":"홍길동",
				 "termsAgreed":true,"privacyAgreed":true}
				""")).andExpect(status().is2xxSuccessful());

		assertThat(jdbcTemplate.queryForList("""
				select c.document_version from member_consent c join member m on m.id = c.member_id
				where m.email = ? and c.consent_type in ('TERMS', 'PRIVACY') order by c.consent_type desc
				""", String.class, "legal-consent@example.com")).containsExactly("2.0", "1.1");
	}

	@Test
	@WithAnonymousUser
	void 비회원은_약관을_바꿀_수_없다() throws Exception {
		save("terms", document("1.0", "약관", TODAY, true)).andExpect(status().isUnauthorized());
	}

	private ResultActions save(String type, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/admin/legal-documents/{type}", type)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String document(String version, String title, LocalDate effectiveAt, boolean published) {
		String body = switch (title) {
			case "첫 약관" -> "첫 약관 본문";
			case "안내" -> "안내 본문";
			default -> title + " 본문";
		};
		return """
				{"version":"%s","title":"%s","body":"%s","effectiveAt":"%s","published":%s}
				""".formatted(version, title, body, effectiveAt, published);
	}
}
