package com.example.shopping;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 푸터의 사업자·고객센터 정보. 따로 표시하지 않은 테스트는 관리자 권한으로 호출한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class CompanyInfoApiTests {

	private static final String COMPANY = """
			{"companyName":" 이음산업건설 ","representative":"홍길동","businessRegistrationNumber":"123-45-67890",
			 "mailOrderSalesNumber":"","address":"서울특별시 중구 세종대로 1","email":"info@example.com",
			 "quoteEmail":" ","privacyOfficer":"김보안","customerCenterPhone":"1588-0000 (내선 2번)",
			 "customerCenterTel":"","customerCenterHours":"평일 09:00 – 18:00 (주말·공휴일 휴무)"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithAnonymousUser
	void 저장하지_않은_사업자_정보는_404다() throws Exception {
		mockMvc.perform(get("/api/v1/company"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"));
	}

	@Test
	void 저장한_사업자_정보는_누구나_보고_비운_선택_항목은_빈_값이다() throws Exception {
		mockMvc.perform(get("/api/v1/admin/company"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());

		save(COMPANY)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.companyName").value("이음산업건설"))
				.andExpect(jsonPath("$.data.mailOrderSalesNumber").value(""))
				.andExpect(jsonPath("$.data.quoteEmail").doesNotExist())
				.andExpect(jsonPath("$.data.customerCenterTel").doesNotExist());

		mockMvc.perform(get("/api/v1/company").with(anonymous()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.businessRegistrationNumber").value("123-45-67890"))
				.andExpect(jsonPath("$.data.privacyOfficer").value("김보안"))
				.andExpect(jsonPath("$.data.customerCenterPhone").value("1588-0000 (내선 2번)"));
		mockMvc.perform(get("/api/v1/admin/company"))
				.andExpect(jsonPath("$.data.address").value("서울특별시 중구 세종대로 1"));
	}

	@Test
	void 형식이_틀린_사업자_정보는_거절한다() throws Exception {
		Map<String, String> invalid = Map.of(
				"\"123-45-67890\"", "\"1234567890\"",
				"\"info@example.com\"", "\"info\"",
				"\"customerCenterTel\":\"\"", "\"customerCenterTel\":\"02-1234-5678\"",
				"\" 이음산업건설 \"", "\" \"");
		for (Map.Entry<String, String> change : invalid.entrySet()) {
			save(COMPANY.replace(change.getKey(), change.getValue()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		}
	}

	@Test
	@WithAnonymousUser
	void 비회원은_사업자_정보를_바꿀_수_없다() throws Exception {
		save(COMPANY).andExpect(status().isUnauthorized());
	}

	private ResultActions save(String body) throws Exception {
		return mockMvc.perform(put("/api/v1/admin/company").contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
