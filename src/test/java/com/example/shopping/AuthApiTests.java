package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTests {

	private static final String EMAIL = "member@example.com";
	private static final String PASSWORD = "test-pass-1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Test
	void 회원가입하면_201과_회원_정보를_돌려주고_비밀번호는_해시로_저장한다() throws Exception {
		signup("Member@Example.com", PASSWORD, "홍길동", "010-1234-5678")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.email").value(EMAIL))
				.andExpect(jsonPath("$.data.name").value("홍길동"))
				.andExpect(content().string(not(containsString(PASSWORD))));

		String storedHash = jdbcTemplate.queryForObject(
				"select password_hash from member where email = ?", String.class, EMAIL);
		assertThat(storedHash).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isTrue();
		assertThat(jdbcTemplate.queryForObject("select phone from member where email = ?", String.class, EMAIL))
				.isEqualTo("01012345678");
		assertThat(jdbcTemplate.queryForObject("select role from member where email = ?", String.class, EMAIL))
				.isEqualTo("USER");
	}

	@Test
	void 이미_가입된_이메일은_대소문자가_달라도_409를_돌려준다() throws Exception {
		signup(EMAIL, PASSWORD, "홍길동", null).andExpect(status().isCreated());

		signup("MEMBER@example.com", PASSWORD, "김철수", null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
	}

	@Test
	void 형식이_잘못된_가입_요청은_400과_필드별_오류를_돌려준다() throws Exception {
		signup("not-an-email", "short", " ", "12345")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItems("email", "password", "name", "phone")));
	}

	@Test
	void 한글이_섞인_비밀번호는_가입할_수_없다() throws Exception {
		signup(EMAIL, "비밀번호password1", "홍길동", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("password"));
	}

	@Test
	void 본문이_없는_요청은_400을_돌려준다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 로그인하면_토큰을_받고_그_토큰으로_로그인이_필요한_API를_호출할_수_있다() throws Exception {
		signup(EMAIL, PASSWORD, "홍길동", null);

		String token = loginAndGetToken("Member@Example.com", PASSWORD);

		mockMvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(EMAIL))
				.andExpect(jsonPath("$.data.role").value("USER"));
	}

	@Test
	void 비밀번호가_틀리거나_없는_이메일이면_똑같이_401을_돌려준다() throws Exception {
		signup(EMAIL, PASSWORD, "홍길동", null);

		login(EMAIL, "wrong-pass-1234")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		login("nobody@example.com", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	void 정지된_계정은_비밀번호가_맞을_때만_정지_사실을_알려준다() throws Exception {
		insertMember(EMAIL, PASSWORD, "USER", "SUSPENDED");

		login(EMAIL, PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
		login(EMAIL, "wrong-pass-1234")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	void 탈퇴한_계정은_로그인할_수_없다() throws Exception {
		insertMember(EMAIL, PASSWORD, "USER", "WITHDRAWN");

		login(EMAIL, PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	void 일반_회원_토큰으로는_관리자_API를_호출할_수_없고_관리자_토큰으로는_호출할_수_있다() throws Exception {
		signup(EMAIL, PASSWORD, "홍길동", null);
		insertMember("admin@example.com", PASSWORD, "ADMIN", "ACTIVE");

		String memberToken = loginAndGetToken(EMAIL, PASSWORD);
		String adminToken = loginAndGetToken("admin@example.com", PASSWORD);

		mockMvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk());
	}

	@Test
	void 변조되거나_만료된_토큰은_401을_돌려준다() throws Exception {
		signup(EMAIL, PASSWORD, "홍길동", null);
		String token = loginAndGetToken(EMAIL, PASSWORD);

		// 서명은 그대로 두고 내용의 권한만 ADMIN으로 바꿔치기한 토큰
		String[] parts = token.split("\\.");
		String forgedPayload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
				.replace("\"USER\"", "\"ADMIN\"");
		assertThat(forgedPayload).contains("\"ADMIN\"");
		String forged = parts[0] + "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
				+ "." + parts[2];
		mockMvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
				.andExpect(status().isUnauthorized());

		Instant anHourAgo = Instant.now().minusSeconds(3600);
		JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
				.subject("1")
				.claim("role", "USER")
				.issuedAt(anHourAgo.minusSeconds(3600))
				.expiresAt(anHourAgo)
				.build();
		String expired = jwtEncoder.encode(
				JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), expiredClaims)).getTokenValue();
		// 프론트가 재발급을 시도할지 판단할 수 있도록 만료는 다른 401과 코드가 다르다.
		mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
		mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void 쓸_수_없는_토큰이_헤더에_있어도_로그인은_된다() throws Exception {
		signup(EMAIL, PASSWORD, "홍길동", null);

		mockMvc.perform(post("/api/v1/auth/login")
						.header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-broken-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("email", EMAIL, "password", PASSWORD)))
				.andExpect(status().isOk());
	}

	private ResultActions signup(String email, String password, String name, String phone) throws Exception {
		String body = phone == null
				? json("email", email, "password", password, "name", name)
				: json("email", email, "password", password, "name", name, "phone", phone);
		return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email, "password", password)));
	}

	private String loginAndGetToken(String email, String password) throws Exception {
		String body = login(email, password)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresIn").value(900))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.accessToken");
	}

	private void insertMember(String email, String password, String role, String status) {
		jdbcTemplate.update("""
				insert into member (email, password_hash, name, role, status, created_at, updated_at)
				values (?, ?, '테스트', ?, ?, now(), now())
				""", email, passwordEncoder.encode(password), role, status);
	}

	/** 키·값을 번갈아 받아 JSON 객체 문자열을 만든다. 테스트 값에는 이스케이프가 필요한 문자를 쓰지 않는다. */
	private String json(String... keyValues) {
		StringBuilder sb = new StringBuilder("{");
		for (int i = 0; i < keyValues.length; i += 2) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
		}
		return sb.append('}').toString();
	}
}
