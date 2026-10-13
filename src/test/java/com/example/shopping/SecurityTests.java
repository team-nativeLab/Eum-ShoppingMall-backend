package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.dto.LoginRequest;
import com.example.shopping.domain.member.dto.PasswordChangeRequest;
import com.example.shopping.domain.member.dto.SignupRequest;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

/** 보안 관련 동작: 로그인 잠금, Refresh 토큰 교체·폐기, 가격 비공개 유지, 운영 엔드포인트 차단 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityTests {

	private static final String EMAIL = "member@example.com";
	private static final String PASSWORD = "test-pass-1234";
	private static final String REFRESH_COOKIE = "refresh_token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EntityManager entityManager;

	// ───────── 로그인 잠금 ─────────

	@Test
	void 비밀번호를_다섯_번_틀리면_맞는_비밀번호로도_로그인할_수_없다가_잠금이_풀리면_다시_된다() throws Exception {
		insertMember(EMAIL, "USER");

		for (int i = 0; i < 5; i++) {
			login(EMAIL, "wrong-pass-" + i)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		}
		login(EMAIL, PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
		// 관리자 로그인 경로로도 우회할 수 없다.
		mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(credentials(EMAIL, PASSWORD)))
				.andExpect(status().isTooManyRequests());

		// 잠금 시간이 지난 상황을 만든다.
		jdbcTemplate.update("update member set locked_until = ? where email = ?",
				LocalDateTime.now().minusSeconds(1), EMAIL);
		entityManager.clear();

		login(EMAIL, PASSWORD).andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select failed_login_count from member where email = ?",
				Integer.class, EMAIL)).isZero();
	}

	@Test
	void 중간에_로그인에_성공하면_실패_횟수가_초기화된다() throws Exception {
		insertMember(EMAIL, "USER");

		for (int round = 0; round < 3; round++) {
			for (int i = 0; i < 4; i++) {
				login(EMAIL, "wrong-pass-1234").andExpect(status().isUnauthorized());
			}
			login(EMAIL, PASSWORD).andExpect(status().isOk());
		}
	}

	// ───────── Refresh 토큰 ─────────

	@Test
	void 로그인하면_스크립트가_읽을_수_없는_쿠키로_Refresh_토큰을_받고_재발급하면_토큰이_교체된다() throws Exception {
		insertMember(EMAIL, "USER");

		MockHttpServletResponse loginResponse = login(EMAIL, PASSWORD).andReturn().getResponse();
		Cookie first = loginResponse.getCookie(REFRESH_COOKIE);
		assertThat(first).isNotNull();
		assertThat(loginResponse.getHeader(HttpHeaders.SET_COOKIE))
				.contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
		// 응답 본문에는 Refresh 토큰이 없고, DB에는 원문이 아닌 해시만 있다.
		assertThat(loginResponse.getContentAsString()).doesNotContain(first.getValue());
		assertThat(jdbcTemplate.queryForList("select token_hash from refresh_token", String.class))
				.hasSize(1)
				.doesNotContain(first.getValue());

		MockHttpServletResponse refreshResponse = refresh(first)
				.andExpect(status().isOk())
				.andReturn().getResponse();
		Cookie second = refreshResponse.getCookie(REFRESH_COOKIE);
		assertThat(second.getValue()).isNotEqualTo(first.getValue());

		String newAccessToken = JsonPath.read(refreshResponse.getContentAsString(), "$.data.accessToken");
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(EMAIL));

		// 한 번 쓴 토큰은 다시 쓸 수 없고, 새 토큰은 계속 쓸 수 있다.
		refresh(first).andExpect(status().isUnauthorized());
		refresh(second).andExpect(status().isOk());
	}

	@Test
	void 사용된_Refresh_토큰이_나중에_다시_쓰이면_탈취로_보고_그_회원의_토큰을_모두_폐기한다() throws Exception {
		insertMember(EMAIL, "USER");
		Cookie stolen = login(EMAIL, PASSWORD).andReturn().getResponse().getCookie(REFRESH_COOKIE);
		Cookie current = refresh(stolen).andReturn().getResponse().getCookie(REFRESH_COOKIE);

		// 사용된 지 한참 지난 토큰이 다시 들어오는 상황을 만든다.
		jdbcTemplate.update("update refresh_token set used_at = ? where used_at is not null",
				LocalDateTime.now().minusMinutes(5));
		entityManager.clear();

		refresh(stolen).andExpect(status().isUnauthorized());
		refresh(current).andExpect(status().isUnauthorized());
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token", Long.class)).isZero();
	}

	@Test
	void 로그아웃하면_Refresh_토큰이_폐기되고_쿠키가_지워진다() throws Exception {
		long memberId = insertMember(EMAIL, "USER");
		Cookie cookie = login(EMAIL, PASSWORD).andReturn().getResponse().getCookie(REFRESH_COOKIE);

		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/logout").with(member(memberId))
						.cookie(cookie))
				.andExpect(status().isOk())
				.andReturn().getResponse();
		assertThat(response.getCookie(REFRESH_COOKIE).getMaxAge()).isZero();

		refresh(cookie).andExpect(status().isUnauthorized());
	}

	@Test
	void 쿠키가_없거나_엉터리거나_만료된_토큰으로는_재발급할_수_없다() throws Exception {
		insertMember(EMAIL, "USER");
		Cookie cookie = login(EMAIL, PASSWORD).andReturn().getResponse().getCookie(REFRESH_COOKIE);

		mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
		refresh(new Cookie(REFRESH_COOKIE, "made-up-token")).andExpect(status().isUnauthorized());

		jdbcTemplate.update("update refresh_token set expires_at = ?", LocalDateTime.now().minusSeconds(1));
		entityManager.clear();
		refresh(cookie).andExpect(status().isUnauthorized());
	}

	@Test
	void 정지된_회원은_남아_있는_Refresh_토큰으로도_재발급받을_수_없다() throws Exception {
		insertMember(EMAIL, "USER");
		Cookie cookie = login(EMAIL, PASSWORD).andReturn().getResponse().getCookie(REFRESH_COOKIE);

		jdbcTemplate.update("update member set status = 'SUSPENDED' where email = ?", EMAIL);
		entityManager.clear();

		refresh(cookie).andExpect(status().isUnauthorized());
	}

	// ───────── 비밀번호 변경 ─────────

	@Test
	void 비밀번호를_바꾸면_새_비밀번호로만_로그인되고_다른_기기의_로그인이_끊긴다() throws Exception {
		long memberId = insertMember(EMAIL, "USER");
		Cookie otherDevice = login(EMAIL, PASSWORD).andReturn().getResponse().getCookie(REFRESH_COOKIE);

		mockMvc.perform(patch("/api/v1/me/password").with(member(memberId))
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"currentPassword":"wrong-pass-1234","newPassword":"new-pass-5678"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
		mockMvc.perform(patch("/api/v1/me/password").with(member(memberId))
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"currentPassword":"%s","newPassword":"short"}
								""".formatted(PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mockMvc.perform(patch("/api/v1/me/password").with(member(memberId))
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"currentPassword":"%s","newPassword":"%s"}
								""".formatted(PASSWORD, PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_NOT_CHANGED"));

		mockMvc.perform(patch("/api/v1/me/password").with(member(memberId))
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"currentPassword":"%s","newPassword":"new-pass-5678"}
								""".formatted(PASSWORD)))
				.andExpect(status().isOk());

		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
		login(EMAIL, "new-pass-5678").andExpect(status().isOk());
		refresh(otherDevice).andExpect(status().isUnauthorized());
	}

	// ───────── 가격 비공개 ─────────

	@Test
	void 가격순으로_정렬해도_금액_미표시_상품은_항상_맨_뒤에_두어_가격대를_짐작할_수_없다() throws Exception {
		insertProduct("저가 상품", 1000, true);
		insertProduct("비공개 중간가 상품", 2000, false);
		insertProduct("고가 상품", 3000, true);
		insertProduct("비공개 최고가 상품", 9000, false);

		mockMvc.perform(get("/api/v1/products").param("sort", "price_asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(4))
				.andExpect(jsonPath("$.data.content[0].name").value("저가 상품"))
				.andExpect(jsonPath("$.data.content[1].name").value("고가 상품"))
				.andExpect(jsonPath("$.data.content[2].name").value("비공개 최고가 상품"))
				.andExpect(jsonPath("$.data.content[3].name").value("비공개 중간가 상품"));
		// 내림차순에서도 비공개 상품끼리의 순서는 가격과 무관하게(최근 등록순) 그대로다.
		mockMvc.perform(get("/api/v1/products").param("sort", "price_desc"))
				.andExpect(jsonPath("$.data.content[0].name").value("고가 상품"))
				.andExpect(jsonPath("$.data.content[1].name").value("저가 상품"))
				.andExpect(jsonPath("$.data.content[2].name").value("비공개 최고가 상품"))
				.andExpect(jsonPath("$.data.content[3].name").value("비공개 중간가 상품"));
	}

	// ───────── 그 밖의 방어 ─────────

	@Test
	void 헬스_체크_외의_운영_엔드포인트는_관리자가_아니면_접근할_수_없다() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/env").with(member(1))).andExpect(status().isForbidden());
	}

	@Test
	void 지나치게_큰_요청_본문은_읽지_않고_413으로_거절한다() throws Exception {
		String hugeBody = "{\"email\":\"a@example.com\",\"password\":\"" + "x".repeat(2 * 1024 * 1024) + "\"}";

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(hugeBody))
				.andExpect(status().is(413))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
	}

	@Test
	void 비밀번호가_담긴_요청_객체는_문자열로_바꿔도_비밀번호가_드러나지_않는다() {
		assertThat(new LoginRequest(EMAIL, PASSWORD).toString()).contains(EMAIL).doesNotContain(PASSWORD);
		assertThat(new SignupRequest(EMAIL, PASSWORD, "홍길동", null).toString()).doesNotContain(PASSWORD);
		assertThat(new PasswordChangeRequest(PASSWORD, "new-pass-5678").toString())
				.doesNotContain(PASSWORD)
				.doesNotContain("new-pass-5678");
	}

	@Test
	void 한_번에_조회할_수_있는_건수는_100건으로_제한된다() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("size", "100000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.size").value(100));
	}

	// ───────── 도우미 ─────────

	private org.springframework.test.web.servlet.request.RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, password)));
	}

	private ResultActions refresh(Cookie cookie) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie));
	}

	private String credentials(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}

	private long insertMember(String email, String role) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '홍길동', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode(PASSWORD), role);
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}

	private void insertProduct(String name, long basePrice, boolean priceVisible) {
		jdbcTemplate.update("""
				insert into product (name, base_price, price_visible, status, created_at, updated_at)
				values (?, ?, ?, 'ON_SALE', now(), now())
				""", name, basePrice, priceVisible);
	}
}
