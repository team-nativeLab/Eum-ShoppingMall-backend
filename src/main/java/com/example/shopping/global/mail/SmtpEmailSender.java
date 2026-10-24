package com.example.shopping.global.mail;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** SMTP로 보낸다. AWS SES SMTP, 사내 메일 서버 등 SMTP를 지원하는 어느 곳이든 쓸 수 있다. */
class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	SmtpEmailSender(JavaMailSender mailSender, String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(String to, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		try {
			mailSender.send(message);
		} catch (MailException e) {
			throw new EmailSendException("Could not send mail", e);
		}
	}
}
