package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

import com.example.shopping.domain.member.dto.request.LoginRequest;
import com.example.shopping.domain.member.dto.request.PasswordChangeRequest;
import com.example.shopping.domain.member.dto.request.SignupRequest;
import com.example.shopping.domain.member.service.RefreshTokenService;
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
	private RefreshTokenService refreshTokenService;

	@Autowired
	private EntityManager entityManager;

	// ───────── 로그인 잠금 ─────────

	@Test
	void 다른_IP의_회원_로그인_실패가_계정을_잠그지_않는다() throws Exception {
		insertMember(EMAIL, "USER");

		for (int i = 0; i < 5; i++) {
			loginFrom("/api/v1/auth/login", EMAIL, "wrong-pass-" + i, "198.51.100.10")
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		}
		loginFrom("/api/v1/auth/login", EMAIL, PASSWORD, "198.51.100.11")
				.andExpect(status().isOk());
	}

	@Test
	void 다른_IP의_관리자_로그인_실패가_관리자_계정을_잠그지_않는다() throws Exception {
		insertMember(EMAIL, "ADMIN");

		for (int i = 0; i < 5; i++) {
			loginFrom("/api/v1/admin/auth/login", EMAIL, "wrong-pass-" + i, "198.51.100.20")
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		}
		loginFrom("/api/v1/admin/auth/login", EMAIL, PASSWORD, "198.51.100.21")
				.andExpect(status().isOk());
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
	void 오래된_사용_토큰과_만료_토큰을_500개씩_나눠_지우고_최근_사용_토큰은_남긴다() {
		long memberId = insertMember(EMAIL, "USER");
		LocalDateTime now = LocalDateTime.now();
		List<Object[]> tokens = new ArrayList<>(1202);
		for (int i = 0; i < 600; i++) {
			tokens.add(new Object[] {memberId, String.format("%064x", i + 1), now.minusDays(2), null});
		}
		for (int i = 0; i < 600; i++) {
			tokens.add(new Object[] {memberId, String.format("%064x", i + 601), now.plusDays(12), now.minusDays(2)});
		}
		tokens.add(new Object[] {memberId, String.format("%064x", 1201), now.plusDays(12), now.minusHours(1)});
		tokens.add(new Object[] {memberId, String.format("%064x", 1202), now.plusDays(12), null});
		jdbcTemplate.batchUpdate("""
				INSERT INTO refresh_token (member_id, token_hash, expires_at, used_at, token_version)
				VALUES (?, ?, ?, ?, 0)
				""", tokens);

		assertThat(refreshTokenService.purgeExpired()).isEqualTo(1000);
		assertThat(refreshTokenService.purgeExpired()).isEqualTo(200);
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token", Long.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where used_at >= ?", Long.class,
				now.minusDays(1))).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where used_at is null", Long.class))
				.isEqualTo(1);
	}

	@Test
	void 로그아웃하면_Access와_Refresh_토큰이_폐기되고_쿠키가_지워진다() throws Exception {
		insertMember(EMAIL, "USER");
		MockHttpServletResponse loginResponse = login(EMAIL, PASSWORD).andReturn().getResponse();
		Cookie cookie = loginResponse.getCookie(REFRESH_COOKIE);
		String accessToken = JsonPath.read(loginResponse.getContentAsString(), "$.data.accessToken");

		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/logout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).cookie(cookie))
				.andExpect(status().isOk())
				.andReturn().getResponse();
		assertThat(response.getCookie(REFRESH_COOKIE).getMaxAge()).isZero();

		refresh(cookie).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void Access_토큰이_만료된_뒤에_로그아웃해도_Refresh_토큰이_폐기된다() throws Exception {
		insertMember(EMAIL, "USER");
		Cookie cookie = login(EMAIL, PASSWORD).andReturn().getResponse().getCookie(REFRESH_COOKIE);

		// 만료되거나 위조된 Access 토큰이 붙어 있어도 로그아웃은 401로 막히지 않는다.
		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/logout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.forged")
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

	@Test
	void 계정이_정지되면_이미_발급된_Access_토큰도_거절한다() throws Exception {
		insertMember(EMAIL, "USER");
		MockHttpServletResponse loginResponse = login(EMAIL, PASSWORD).andReturn().getResponse();
		String accessToken = JsonPath.read(loginResponse.getContentAsString(), "$.data.accessToken");

		jdbcTemplate.update("update member set status = 'SUSPENDED' where email = ?", EMAIL);
		entityManager.clear();

		mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isUnauthorized());
	}

	// ───────── 비밀번호 변경 ─────────

	@Test
	void 비밀번호를_바꾸면_새_비밀번호로만_로그인되고_다른_기기의_로그인이_끊긴다() throws Exception {
		long memberId = insertMember(EMAIL, "USER");
		MockHttpServletResponse otherDeviceLogin = login(EMAIL, PASSWORD).andReturn().getResponse();
		Cookie otherDevice = otherDeviceLogin.getCookie(REFRESH_COOKIE);
		String oldAccessToken = JsonPath.read(otherDeviceLogin.getContentAsString(), "$.data.accessToken");

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

		// A pre-change refresh generation must not mint a usable token after the password update.
		String lateRefresh = refreshTokenService.issue(memberId, 0);
		refresh(new Cookie(REFRESH_COOKIE, lateRefresh)).andExpect(status().isUnauthorized());

		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
		login(EMAIL, "new-pass-5678").andExpect(status().isOk());
		refresh(otherDevice).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldAccessToken))
				.andExpect(status().isUnauthorized());
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
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/env").with(member(1))).andExpect(status().isForbidden());
	}

	@Test
	void 허용되지_않은_Origin의_Refresh_쿠키_요청은_거부한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://evil.example"))
				.andExpect(status().isForbidden());
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

	@Test
	void 터무니없이_큰_페이지_번호는_500이_아니라_400으로_거절한다() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("page", "99999999").param("size", "100"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/notices").param("page", "99999999").param("size", "100"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/admin/products").with(admin()).param("page", "99999999").param("size", "100"))
				.andExpect(status().isBadRequest());
		// 데이터가 없는 먼 페이지라도 표현할 수 있는 위치면 빈 목록을 돌려준다.
		mockMvc.perform(get("/api/v1/products").param("page", "1000000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(0));
	}

	@Test
	void 형식이_틀린_파라미터의_오류_메시지에_서버_내부_클래스_이름이_드러나지_않는다() throws Exception {
		mockMvc.perform(get("/api/v1/products").param("categoryId", "abc").param("certified", "maybe"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(2))
				.andExpect(jsonPath("$.errors[*].reason").value(everyItem(is("값의 형식이 올바르지 않습니다."))))
				.andExpect(content().string(not(containsString("java."))));
	}

	// ───────── 도우미 ─────────

	private org.springframework.test.web.servlet.request.RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
		return jwt().jwt(token -> token.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, password)));
	}

	private ResultActions loginFrom(String path, String email, String password, String remoteAddress) throws Exception {
		return mockMvc.perform(post(path).with(request -> {
			request.setRemoteAddr(remoteAddress);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)));
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
