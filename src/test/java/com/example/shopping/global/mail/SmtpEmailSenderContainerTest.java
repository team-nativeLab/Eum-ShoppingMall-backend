package com.example.shopping.global.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.shopping.global.mail.EmailSender.EmailSendException;
import com.jayway.jsonpath.JsonPath;

/** 테스트용 메일 서버(Mailpit)를 컨테이너로 띄워 SMTP로 실제로 보내 본다. Docker가 없으면 건너뛴다. */
@Testcontainers(disabledWithoutDocker = true)
class SmtpEmailSenderContainerTest {

	@Container
	private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.21.8")
			.withExposedPorts(1025, 8025);

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void SMTP로_보낸_메일이_한글_제목과_본문_그대로_도착한다() throws Exception {
		EmailSender sender = new MailConfiguration().emailSender(MAILPIT.getHost(), MAILPIT.getMappedPort(1025), "",
				"", "이음 쇼핑몰 <no-reply@example.com>");

		sender.send("member@example.com", "[이음 쇼핑몰] 이메일 인증번호", "인증번호: 123456");

		String messages = get("/api/v1/messages");
		assertThat(JsonPath.<Integer>read(messages, "$.total")).isEqualTo(1);
		String id = JsonPath.read(messages, "$.messages[0].ID");
		String message = get("/api/v1/message/" + id);
		assertThat(JsonPath.<String>read(message, "$.Subject")).isEqualTo("[이음 쇼핑몰] 이메일 인증번호");
		assertThat(JsonPath.<String>read(message, "$.From.Address")).isEqualTo("no-reply@example.com");
		assertThat(JsonPath.<List<String>>read(message, "$.To[*].Address")).containsExactly("member@example.com");
		assertThat(JsonPath.<String>read(message, "$.Text")).contains("인증번호: 123456");
	}

	@Test
	void 메일_서버가_받지_않으면_발송_실패로_알린다() {
		// 아무것도 듣고 있지 않은 포트
		EmailSender sender = new MailConfiguration().emailSender("127.0.0.1", 1, "", "", "no-reply@example.com");

		assertThatThrownBy(() -> sender.send("member@example.com", "제목", "본문"))
				.isInstanceOf(EmailSendException.class);
	}

	@Test
	void 메일_서버_설정이_없으면_보내지_않고_로그만_남긴다() {
		assertThat(new MailConfiguration().emailSender("", 587, "", "", "")).isInstanceOf(LoggingEmailSender.class);
	}

	private String get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(
				URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path)).build();
		return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
	}
}
