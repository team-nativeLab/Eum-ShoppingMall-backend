package com.example.shopping.global.mail;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration
public class MailConfiguration {

	// 메일 서버가 응답하지 않아도 요청이 오래 묶이지 않게 한다.
	private static final String TIMEOUT_MILLIS = "5000";

	@Bean
	EmailSender emailSender(@Value("${app.mail.host:}") String host, @Value("${app.mail.port:587}") int port,
			@Value("${app.mail.username:}") String username, @Value("${app.mail.password:}") String password,
			@Value("${app.mail.from:}") String from) {
		if (!StringUtils.hasText(host)) {
			return new LoggingEmailSender();
		}
		Assert.hasText(from, "MAIL_FROM is required when MAIL_HOST is set");
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost(host);
		mailSender.setPort(port);
		mailSender.setDefaultEncoding("UTF-8");
		Properties properties = mailSender.getJavaMailProperties();
		if (StringUtils.hasText(username)) {
			mailSender.setUsername(username);
			mailSender.setPassword(password);
			properties.put("mail.smtp.auth", "true");
			properties.put("mail.smtp.starttls.enable", "true");
			properties.put("mail.smtp.starttls.required", "true");
		}
		properties.put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
		properties.put("mail.smtp.timeout", TIMEOUT_MILLIS);
		properties.put("mail.smtp.writetimeout", TIMEOUT_MILLIS);
		return new SmtpEmailSender(mailSender, from);
	}
}
