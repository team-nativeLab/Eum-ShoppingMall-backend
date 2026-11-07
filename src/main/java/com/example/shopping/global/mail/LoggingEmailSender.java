package com.example.shopping.global.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 메일 서버 설정이 없을 때(로컬 개발·테스트) 쓴다. 보내지 않고 로그만 남긴다.
 * 받는 사람과 본문(인증번호 등)은 로그 수준과 상관없이 남기지 않는다(#139). 운영에서 MAIL_HOST가 빠진 채
 * DEBUG 로그를 켜도 인증번호가 로그에 남지 않게 하려는 것이다. 로컬에서 인증번호가 필요하면 메일 서버(예: MailHog)를 띄운다.
 */
class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(String to, String subject, String text) {
		log.warn("메일 서버 설정(MAIL_HOST)이 없어 메일을 보내지 않았습니다: subject={}", subject);
	}
}
