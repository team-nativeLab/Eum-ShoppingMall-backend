package com.example.shopping.global.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 메일 서버 설정이 없을 때(로컬 개발·테스트) 쓴다. 보내지 않고 로그만 남긴다.
 * 본문에는 인증번호가 들어 있으므로 DEBUG 로그로만 남긴다.
 */
class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(String to, String subject, String text) {
		log.warn("메일 서버 설정(MAIL_HOST)이 없어 메일을 보내지 않았습니다: to={}, subject={}", to, subject);
		log.debug("보내지 않은 메일 본문: {}", text);
	}
}
