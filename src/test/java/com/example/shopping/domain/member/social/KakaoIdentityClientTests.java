package com.example.shopping.domain.member.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

/** 카카오 로그인 확인(#225). 카카오 서버 대신 정해 둔 응답을 쓴다. */
class KakaoIdentityClientTests {

	private static final String REDIRECT_URI = "https://shop.example/auth/callback";
	private static final String USER = """
			{"id":1001,"kakao_account":{"email":"buyer@example.com","is_email_valid":true,"is_email_verified":%s,
			 "profile":{"nickname":"길동이"}}}
			""";

	private MockRestServiceServer server;
	private RestClient restClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
	}

	@Test
	void 인가_코드를_액세스_토큰으로_바꿔_카카오_사용자를_읽는다() {
		KakaoIdentityClient client = client("123", null);
		server.expect(requestTo(KakaoIdentityClient.TOKEN_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().formDataContains(Map.of("grant_type", "authorization_code", "client_id",
						"rest-key", "redirect_uri", REDIRECT_URI, "code", "the-code", "client_secret", "kakao-secret")))
				.andRespond(withSuccess("{\"access_token\":\"kakao-at\",\"token_type\":\"bearer\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo(KakaoIdentityClient.USER_URL))
				.andExpect(header("Authorization", "Bearer kakao-at"))
				.andRespond(withSuccess(USER.formatted("true"), MediaType.APPLICATION_JSON));

		SocialProfile profile = client.verifyAuthorizationCode("the-code", REDIRECT_URI, null);

		assertThat(profile).isEqualTo(new SocialProfile(SocialProvider.KAKAO, "1001", "buyer@example.com", true,
				"길동이"));
		server.verify();
	}

	@Test
	void 앱_토큰은_우리_앱에서_받은_것만_받는다() {
		server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URL))
				.andExpect(header("Authorization", "Bearer other-app-token"))
				.andRespond(withSuccess("{\"id\":1001,\"app_id\":999,\"expires_in\":3600}", MediaType.APPLICATION_JSON));
		assertFailed(() -> client("123", null).verifyAppToken("other-app-token"), ErrorCode.SOCIAL_LOGIN_FAILED);
		server.verify();

		server.reset();
		server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URL))
				.andRespond(withSuccess("{\"id\":1001,\"app_id\":123}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(KakaoIdentityClient.USER_URL))
				.andRespond(withSuccess(USER.formatted("false"), MediaType.APPLICATION_JSON));
		// 인증되지 않은 이메일은 인증된 이메일로 보지 않는다.
		assertThat(client("123", null).verifyAppToken("our-app-token").emailVerified()).isFalse();
	}

	@Test
	void 토큰이_틀리면_401_카카오에_연결하지_못하면_503이다() {
		server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		assertFailed(() -> client("123", null).verifyAppToken("expired"), ErrorCode.SOCIAL_LOGIN_FAILED);

		server.reset();
		server.expect(requestTo(KakaoIdentityClient.TOKEN_URL)).andRespond(withServerError());
		assertFailed(() -> client("123", null).verifyAuthorizationCode("the-code", REDIRECT_URI, null),
				ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
	}

	@Test
	void 연결_끊기는_Admin_키로_부르고_키가_없으면_부르지_않는다() {
		server.expect(requestTo(KakaoIdentityClient.UNLINK_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "KakaoAK admin-key"))
				.andExpect(content().formDataContains(Map.of("target_id_type", "user_id", "target_id", "1001")))
				.andRespond(withSuccess("{\"id\":1001}", MediaType.APPLICATION_JSON));
		client("123", "admin-key").unlink("1001");
		server.verify();

		server.reset();
		client("123", null).unlink("1001");
		server.verify();
	}

	@Test
	void 키가_없으면_503이다() {
		KakaoIdentityClient unconfigured = new KakaoIdentityClient(restClient,
				new SocialLoginProperties.Kakao(null, null, null, null));
		assertFailed(() -> unconfigured.verifyAuthorizationCode("code", REDIRECT_URI, null),
				ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
		assertFailed(() -> unconfigured.verifyAppToken("token"), ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
	}

	private KakaoIdentityClient client(String appId, String adminKey) {
		return new KakaoIdentityClient(restClient,
				new SocialLoginProperties.Kakao("rest-key", "kakao-secret", appId, adminKey));
	}

	private static void assertFailed(Runnable call, ErrorCode expected) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}
}
