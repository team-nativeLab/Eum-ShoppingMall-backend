package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** 회원 배송지 주소록 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberAddressApiTests {

	private static final String PASSWORD = "test-pass-1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void 처음_등록한_배송지가_기본_배송지가_되고_다른_배송지를_기본으로_정하면_하나만_남는다() throws Exception {
		RequestPostProcessor member = member(insertMember("address@example.com"));

		long office = idOf(create(member, address("회사", false)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.isDefault").value(true))
				.andExpect(jsonPath("$.data.recipientPhone").value("01012345678")));
		long site = idOf(create(member, address("현장", false))
				.andExpect(jsonPath("$.data.isDefault").value(false)));

		mockMvc.perform(put("/api/v1/me/addresses/{id}", site).with(member).contentType(MediaType.APPLICATION_JSON)
						.content(address("현장 2공구", true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.label").value("현장 2공구"))
				.andExpect(jsonPath("$.data.isDefault").value(true));

		// 기본 배송지가 맨 앞이고, 기본 배송지는 하나뿐이다.
		mockMvc.perform(get("/api/v1/me/addresses").with(member))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].id").value(site))
				.andExpect(jsonPath("$.data[0].isDefault").value(true))
				.andExpect(jsonPath("$.data[1].id").value(office))
				.andExpect(jsonPath("$.data[1].isDefault").value(false))
				// 주문 요청의 배송지(delivery)와 같은 필드 이름이라 그대로 넣을 수 있다.
				.andExpect(jsonPath("$.data[1].recipient").value("홍길동"))
				.andExpect(jsonPath("$.data[1].zipCode").value("06236"))
				.andExpect(jsonPath("$.data[1].address").value("서울특별시 강남구 테헤란로 1"))
				.andExpect(jsonPath("$.data[1].addressDetail").value("10층"));

		// 기본 배송지를 지우면 남은 배송지가 기본이 된다.
		mockMvc.perform(delete("/api/v1/me/addresses/{id}", site).with(member)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/addresses").with(member))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].id").value(office))
				.andExpect(jsonPath("$.data[0].isDefault").value(true));
	}

	@Test
	void 남의_배송지는_보거나_고치거나_지울_수_없고_없는_배송지와_똑같이_404다() throws Exception {
		RequestPostProcessor owner = member(insertMember("owner@example.com"));
		RequestPostProcessor other = member(insertMember("other@example.com"));
		long addressId = idOf(create(owner, address("회사", false)));

		mockMvc.perform(get("/api/v1/me/addresses").with(other))
				.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(put("/api/v1/me/addresses/{id}", addressId).with(other).contentType(MediaType.APPLICATION_JSON)
						.content(address("가로채기", true)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
		mockMvc.perform(delete("/api/v1/me/addresses/{id}", addressId).with(other))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/me/addresses/{id}", 999_999L).with(owner))
				.andExpect(status().isNotFound());

		assertThat(jdbcTemplate.queryForObject("select label from member_address where id = ?", String.class,
				addressId)).isEqualTo("회사");
	}

	@Test
	void 배송지는_10개까지_저장하고_형식이_틀리면_400이다() throws Exception {
		RequestPostProcessor member = member(insertMember("many@example.com"));
		for (int i = 0; i < 10; i++) {
			create(member, address("배송지 " + i, false)).andExpect(status().isCreated());
		}
		create(member, address("열한 번째", false))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_EXCEEDED"));

		RequestPostProcessor another = member(insertMember("invalid@example.com"));
		create(another, """
				{"label":"회사","recipient":"홍길동","recipientPhone":"abc","zipCode":"1234","address":"서울"}
				""").andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/me/addresses")).andExpect(status().isUnauthorized());
	}

	@Test
	void 탈퇴하면_배송지를_모두_지운다() throws Exception {
		long memberId = insertMember("leaving@example.com");
		RequestPostProcessor member = member(memberId);
		create(member, address("회사", false));
		create(member, address("집", false));

		mockMvc.perform(delete("/api/v1/me").with(member).contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForObject("select count(*) from member_address where member_id = ?", Long.class,
				memberId)).isZero();
	}

	private ResultActions create(RequestPostProcessor member, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/me/addresses").with(member).contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static String address(String label, boolean isDefault) {
		return """
				{"label":"%s","recipient":" 홍길동 ","recipientPhone":"010-1234-5678","zipCode":"06236",
				 "address":"서울특별시 강남구 테헤란로 1","addressDetail":"10층","isDefault":%s}
				""".formatted(label, isDefault);
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
				.longValue();
	}

	private RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private long insertMember(String email) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, phone, role, status, created_at, updated_at)
				values (?, ?, '홍길동', '01012345678', 'USER', 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD));
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}
}
