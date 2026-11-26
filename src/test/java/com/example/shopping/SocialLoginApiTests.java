package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.domain.member.service.SocialSignupTicketCleaner;
import com.example.shopping.domain.member.social.GoogleIdentityClient;
import com.example.shopping.domain.member.social.KakaoIdentityClient;
import com.example.shopping.domain.member.social.SocialProfile;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** 카카오·구글 로그인(#225). 카카오·구글 확인은 대역으로 바꾸고, 그 뒤의 가입·연결·로그인을 실제로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "app.social.redirect-uris=https://shop.example/auth/callback")
class SocialLoginApiTests {

	private static final String REDIRECT_URI = "https://shop.example/auth/callback";
	private static final String SIGNUP = """
			{"signupToken":"%s","name":"홍길동","phone":"010-1234-5678","termsAgreed":true,"privacyAgreed":true}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private SocialSignupTicketCleaner signupTicketCleaner;

	@MockitoBean
	private KakaoIdentityClient kakaoClient;

	@MockitoBean
	private GoogleIdentityClient googleClient;

	@Test
	void 처음_온_사람은_가입_토큰을_받고_동의하면_가입하고_다음부터는_바로_로그인한다() throws Exception {
		given(kakaoClient.verifyAuthorizationCode("code-1", REDIRECT_URI, null))
				.willReturn(new SocialProfile(SocialProvider.KAKAO, "1001", " New@Example.com ", true, "길동이"));

		String signupToken = JsonPath.read(webLogin("kakao", "code-1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.signupRequired").value(true))
				.andExpect(jsonPath("$.data.email").value("new@example.com"))
				.andExpect(jsonPath("$.data.name").value("길동이"))
				.andExpect(jsonPath("$.data.provider").value("KAKAO"))
				.andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andReturn().getResponse().getContentAsString(), "$.data.signupToken");

		String accessToken = JsonPath.read(postJson("/api/v1/auth/social/signup", SIGNUP.formatted(signupToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").exists())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
				.andReturn().getResponse().getContentAsString(), "$.data.accessToken");

		// 카카오가 인증한 이메일이라 이메일 인증을 마친 회원이 되고, 약관 동의가 남는다.
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from member where email = 'new@example.com' and email_verified_at is not null",
				Integer.class)).isEqualTo(1);
		long memberId = memberId("new@example.com");
		assertThat(jdbcTemplate.queryForObject("select count(*) from member_consent where member_id = ?",
				Integer.class, memberId)).isEqualTo(3);
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("홍길동"))
				.andExpect(jsonPath("$.data.hasPassword").value(false));

		// 가입 토큰은 한 번만 쓴다.
		postJson("/api/v1/auth/social/signup", SIGNUP.formatted(signupToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SOCIAL_SIGNUP_TOKEN_INVALID"));
		// 비밀번호가 없어 이메일 로그인은 할 수 없다.
		postJson("/api/v1/auth/login", "{\"email\":\"new@example.com\",\"password\":\"anything-123\"}")
				.andExpect(status().isUnauthorized());

		given(kakaoClient.verifyAuthorizationCode("code-2", REDIRECT_URI, null))
				.willReturn(new SocialProfile(SocialProvider.KAKAO, "1001", "new@example.com", true, "길동이"));
		webLogin("kakao", "code-2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.signupRequired").value(false))
				.andExpect(jsonPath("$.data.accessToken").exists())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")));
	}

	@Test
	void 같은_이메일의_회원은_인증된_이메일이면_연결하고_아니면_이메일_로그인을_안내한다() throws Exception {
		long memberId = insertMember("buyer@example.com", "USER");
		given(googleClient.verifyAuthorizationCode("code-g", REDIRECT_URI, "verifier"))
				.willReturn(new SocialProfile(SocialProvider.GOOGLE, "google-sub", "buyer@example.com", true, "구매자"));
		given(kakaoClient.verifyAuthorizationCode("code-k", REDIRECT_URI, null))
				.willReturn(new SocialProfile(SocialProvider.KAKAO, "2002", "buyer@example.com", false, "구매자"));

		postJson("/api/v1/auth/social/google/login", """
				{"code":"code-g","redirectUri":"%s","codeVerifier":"verifier"}
				""".formatted(REDIRECT_URI))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.signupRequired").value(false));
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from member_social_account where member_id = ? and provider = 'GOOGLE'", Integer.class,
				memberId)).isEqualTo(1);

		webLogin("kakao", "code-k")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_NOT_VERIFIED"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from member_social_account where provider = 'KAKAO'",
				Integer.class)).isZero();
	}

	@Test
	void 관리자_계정에는_연결하지_않고_감사_로그에_남긴다() throws Exception {
		insertMember("admin@example.com", "ADMIN");
		given(googleClient.verifyAuthorizationCode("code-admin", REDIRECT_URI, null))
				.willReturn(new SocialProfile(SocialProvider.GOOGLE, "admin-sub", "admin@example.com", true, "관리자"));

		webLogin("google", "code-admin")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SOCIAL_LOGIN_FAILED"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from member_social_account", Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from admin_audit_log where summary = '관리자 계정 소셜 로그인 시도(거절)'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void 등록하지_않은_돌아올_주소와_모르는_제공자와_이메일이_없는_계정은_거절한다() throws Exception {
		postJson("/api/v1/auth/social/kakao/login", """
				{"code":"code","redirectUri":"https://evil.example/callback"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SOCIAL_REDIRECT_URI_NOT_ALLOWED"));
		verify(kakaoClient, never()).verifyAuthorizationCode(any(), any(), any());

		webLogin("naver", "code")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SOCIAL_PROVIDER_NOT_SUPPORTED"));

		given(kakaoClient.verifyAuthorizationCode("no-email", REDIRECT_URI, null))
				.willReturn(new SocialProfile(SocialProvider.KAKAO, "3003", null, false, "이메일없음"));
		webLogin("kakao", "no-email")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_REQUIRED"));

		given(kakaoClient.verifyAuthorizationCode("bad-code", REDIRECT_URI, null))
				.willThrow(new BusinessException(ErrorCode.SOCIAL_LOGIN_FAILED));
		webLogin("kakao", "bad-code").andExpect(status().isUnauthorized());
	}

	@Test
	void 가입_토큰이_지나면_가입할_수_없다() throws Exception {
		String signupToken = newSignupToken("4004", "late@example.com");
		jdbcTemplate.update("update social_signup_ticket set expires_at = ?", LocalDateTime.now().minusMinutes(1));
		// 테스트는 한 트랜잭션이라 JPA가 들고 있는 가입 대기를 비워야 DB에서 바꾼 만료 시각을 읽는다.
		entityManager.clear();

		postJson("/api/v1/auth/social/signup", SIGNUP.formatted(signupToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SOCIAL_SIGNUP_TOKEN_INVALID"));
		postJson("/api/v1/auth/social/signup", SIGNUP.formatted("unknown-token")).andExpect(status().isBadRequest());
		// 쓰지 않고 지난 가입 대기는 정기 작업이 지운다(제공자가 알려 준 이메일·이름을 오래 두지 않는다).
		newSignupToken("4005", "unused@example.com");
		jdbcTemplate.update("update social_signup_ticket set expires_at = ?", LocalDateTime.now().minusMinutes(1));
		assertThat(signupTicketCleaner.purge(LocalDateTime.now())).isEqualTo(1);
		// 약관 동의는 필수다.
		postJson("/api/v1/auth/social/signup", """
				{"signupToken":"token","name":"홍길동","termsAgreed":false,"privacyAgreed":true}
				""").andExpect(status().isBadRequest());
	}

	@Test
	void 앱은_SDK_토큰으로_가입하고_로그인하며_토큰을_모두_본문으로_받는다() throws Exception {
		given(googleClient.verifyAppToken("google-id-token"))
				.willReturn(new SocialProfile(SocialProvider.GOOGLE, "app-sub", "app@example.com", true, "앱사용자"));

		String signupToken = JsonPath.read(postJson("/api/v1/app/auth/social/google/login",
				"{\"token\":\"google-id-token\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.signupRequired").value(true))
				.andReturn().getResponse().getContentAsString(), "$.data.signupToken");
		postJson("/api/v1/app/auth/social/signup", SIGNUP.formatted(signupToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").exists())
				.andExpect(jsonPath("$.data.refreshToken").exists())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		postJson("/api/v1/app/auth/social/google/login", "{\"token\":\"google-id-token\"}")
				.andExpect(jsonPath("$.data.signupRequired").value(false))
				.andExpect(jsonPath("$.data.refreshToken").exists());

		// 브라우저 요청은 받지 않는다(웹 페이지가 앱 API로 Refresh 토큰을 받지 못하게).
		mockMvc.perform(post("/api/v1/app/auth/social/google/login").header(HttpHeaders.ORIGIN, "http://localhost:3000")
						.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"google-id-token\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void 비밀번호가_없는_회원은_마지막_연결을_끊을_수_없고_비밀번호_없이_탈퇴한다() throws Exception {
		postJson("/api/v1/auth/social/signup", SIGNUP.formatted(newSignupToken("5005", "social@example.com")))
				.andExpect(status().isOk());
		long socialOnly = memberId("social@example.com");

		mockMvc.perform(get("/api/v1/me/social-accounts").with(member(socialOnly)))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].provider").value("KAKAO"))
				.andExpect(jsonPath("$.data[0].email").value("social@example.com"));
		mockMvc.perform(delete("/api/v1/me/social-accounts/kakao").with(member(socialOnly)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SOCIAL_UNLINK_NOT_ALLOWED"));
		mockMvc.perform(patch("/api/v1/me/password").with(member(socialOnly)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"whatever-1\",\"newPassword\":\"new-password-1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));

		mockMvc.perform(delete("/api/v1/me").with(member(socialOnly)).contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select count(*) from member_social_account where member_id = ?",
				Integer.class, socialOnly)).isZero();
	}

	@Test
	void 비밀번호가_있는_회원은_연결을_끊을_수_있고_탈퇴에는_비밀번호가_필요하다() throws Exception {
		long memberId = insertMember("buyer@example.com", "USER");
		given(googleClient.verifyAuthorizationCode("code-g", REDIRECT_URI, null))
				.willReturn(new SocialProfile(SocialProvider.GOOGLE, "google-sub", "buyer@example.com", true, "구매자"));
		webLogin("google", "code-g").andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/me/social-accounts/google").with(member(memberId)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/social-accounts").with(member(memberId)))
				.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(delete("/api/v1/me/social-accounts/google").with(member(memberId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_NOT_LINKED"));
		mockMvc.perform(delete("/api/v1/me").with(member(memberId)).contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
	}

	private String newSignupToken(String kakaoId, String email) throws Exception {
		given(kakaoClient.verifyAuthorizationCode("code-" + kakaoId, REDIRECT_URI, null))
				.willReturn(new SocialProfile(SocialProvider.KAKAO, kakaoId, email, true, "홍길동"));
		return JsonPath.read(webLogin("kakao", "code-" + kakaoId)
				.andExpect(jsonPath("$.data.signupRequired").value(true))
				.andReturn().getResponse().getContentAsString(), "$.data.signupToken");
	}

	private ResultActions webLogin(String provider, String code) throws Exception {
		return postJson("/api/v1/auth/social/" + provider + "/login", """
				{"code":"%s","redirectUri":"%s"}
				""".formatted(code, REDIRECT_URI));
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private long insertMember(String email, String role) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, phone, role, status, created_at, updated_at)
				values (?, ?, '구매자', '01012345678', ?, 'ACTIVE', now(), now())
				""", email, passwordEncoder.encode("password-123"), role);
		return memberId(email);
	}

	private long memberId(String email) {
		return jdbcTemplate.queryForObject("select id from member where email = ?", Long.class, email);
	}

	private static RequestPostProcessor member(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}
}
