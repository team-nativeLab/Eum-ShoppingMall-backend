package com.example.shopping.global.mail;

/** 고객에게 메일을 보낸다. 메일 서버 설정(MAIL_HOST)이 없으면 보내지 않고 로그만 남긴다. */
public interface EmailSender {

	/** @throws EmailSendException 메일 서버가 받지 않은 경우 */
	void send(String to, String subject, String text);

	class EmailSendException extends RuntimeException {

		public EmailSendException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
