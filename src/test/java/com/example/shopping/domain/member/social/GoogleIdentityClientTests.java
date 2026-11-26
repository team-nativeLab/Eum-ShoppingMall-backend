package com.example.shopping.domain.member.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.shopping.domain.member.entity.SocialProvider;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/** 구글 로그인 확인(#225). 구글 대신 테스트 키로 서명한 ID 토큰과 토큰 교환 응답을 쓴다. */
class GoogleIdentityClientTests {

	private static final String REDIRECT_URI = "https://shop.example/auth/callback";

	private JwtEncoder encoder;
	private MockRestServiceServer server;
	private GoogleIdentityClient client;

	@BeforeEach
	void setUp() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
				.privateKey((RSAPrivateKey) keyPair.getPrivate()).keyID("test").build();
		encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GoogleIdentityClient(builder.build(),
				new SocialLoginProperties.Google("web-client", "web-secret", List.of("android-client")),
				NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build());
	}

	@Test
	void 인가_코드를_교환해_ID_토큰의_사용자를_확인한다() {
		server.expect(requestTo(GoogleIdentityClient.TOKEN_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().formDataContains(Map.of("grant_type", "authorization_code", "code", "the-code",
						"client_id", "web-client", "client_secret", "web-secret", "redirect_uri", REDIRECT_URI,
						"code_verifier", "the-verifier")))
				.andRespond(withSuccess("{\"id_token\":\"" + idToken("https://accounts.google.com", "web-client", true)
						+ "\",\"access_token\":\"x\"}", MediaType.APPLICATION_JSON));

		SocialProfile profile = client.verifyAuthorizationCode("the-code", REDIRECT_URI, "the-verifier");

		assertThat(profile).isEqualTo(new SocialProfile(SocialProvider.GOOGLE, "google-sub-1", "buyer@example.com",
				true, "홍길동"));
		server.verify();
	}

	@Test
	void 앱은_앱_클라이언트용_ID_토큰을_받고_다른_대상이나_발급자의_토큰은_받지_않는다() {
		assertThat(client.verifyAppToken(idToken("accounts.google.com", "android-client", false)).emailVerified())
				.isFalse();

		assertFailed(() -> client.verifyAppToken(idToken("https://accounts.google.com", "other-app", true)),
				ErrorCode.SOCIAL_LOGIN_FAILED);
		assertFailed(() -> client.verifyAppToken(idToken("https://evil.example", "android-client", true)),
				ErrorCode.SOCIAL_LOGIN_FAILED);
		assertFailed(() -> client.verifyAppToken("not-a-jwt"), ErrorCode.SOCIAL_LOGIN_FAILED);
	}

	@Test
	void 코드_교환이_거절되면_401_구글에_연결하지_못하면_503이다() {
		server.expect(requestTo(GoogleIdentityClient.TOKEN_URL)).andRespond(withBadRequest());
		assertFailed(() -> client.verifyAuthorizationCode("used-code", REDIRECT_URI, null),
				ErrorCode.SOCIAL_LOGIN_FAILED);

		server.reset();
		server.expect(requestTo(GoogleIdentityClient.TOKEN_URL)).andRespond(withServerError());
		assertFailed(() -> client.verifyAuthorizationCode("the-code", REDIRECT_URI, null),
				ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
	}

	@Test
	void 키가_없으면_503이다() {
		GoogleIdentityClient unconfigured = new GoogleIdentityClient(RestClient.create(),
				new SocialLoginProperties.Google(null, null, List.of()), token -> {
					throw new IllegalStateException("호출하지 않아야 한다");
				});

		assertFailed(() -> unconfigured.verifyAuthorizationCode("code", REDIRECT_URI, null),
				ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
		assertFailed(() -> unconfigured.verifyAppToken("token"), ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
	}

	private String idToken(String issuer, String audience, boolean emailVerified) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.audience(List.of(audience))
				.subject("google-sub-1")
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("email", "buyer@example.com")
				.claim("email_verified", emailVerified)
				.claim("name", "홍길동")
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(),
				claims)).getTokenValue();
	}

	private static void assertFailed(Runnable call, ErrorCode expected) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}
}
