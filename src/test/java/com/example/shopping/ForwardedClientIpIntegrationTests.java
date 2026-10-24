package com.example.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Verifies that an untrusted direct caller cannot rotate X-Forwarded-For to evade the IP limit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"security.rate-limit.requests-per-minute=1",
		"server.tomcat.remoteip.internal-proxies=10.20.0.1/32",
		"spring.data.redis.connect-timeout=200ms",
		"spring.data.redis.timeout=200ms" })
@ActiveProfiles("test")
class ForwardedClientIpIntegrationTests {

	@LocalServerPort
	private int port;

	@Test
	void 신뢰하지_않는_직접_요청의_XForwardedFor_변경으로_한도를_우회할_수_없다() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		assertThat(send(client, "198.51.100.10")).isEqualTo(401);
		assertThat(send(client, "198.51.100.11")).isEqualTo(429);
	}

	private int send(HttpClient client, String forwardedFor) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
				.header("Content-Type", "application/json")
				.header("X-Forwarded-For", forwardedFor)
				.POST(HttpRequest.BodyPublishers.ofString("""
						{"email":"nobody@example.com","password":"wrong-pass-1234"}
						"""))
				.build();
		return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}
}
