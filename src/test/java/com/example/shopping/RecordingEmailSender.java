package com.example.shopping;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.example.shopping.global.mail.EmailSender;

/** 보낸 메일을 메모리에 남겨 테스트에서 인증번호를 꺼내 쓴다. */
class RecordingEmailSender implements EmailSender {

	private static final Pattern CODE = Pattern.compile("인증번호: (\\d{6})");

	record SentMail(String to, String subject, String text) {

		String code() {
			Matcher matcher = CODE.matcher(text);
			if (!matcher.find()) {
				throw new IllegalStateException("메일에 인증번호가 없습니다: " + text);
			}
			return matcher.group(1);
		}
	}

	private final List<SentMail> sent = new CopyOnWriteArrayList<>();

	@Override
	public void send(String to, String subject, String text) {
		sent.add(new SentMail(to, subject, text));
	}

	List<SentMail> sentTo(String to) {
		return sent.stream().filter(mail -> mail.to().equals(to)).toList();
	}

	SentMail lastTo(String to) {
		List<SentMail> mails = sentTo(to);
		if (mails.isEmpty()) {
			throw new IllegalStateException("보낸 메일이 없습니다: " + to);
		}
		return mails.get(mails.size() - 1);
	}

	void clear() {
		sent.clear();
	}

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		RecordingEmailSender recordingEmailSender() {
			return new RecordingEmailSender();
		}
	}
}
