package com.example.shopping.global.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/** 메일 서버 설정이 없을 때 남기는 로그에 받는 사람과 본문(인증번호)이 없는지 확인한다 (#139). */
@ExtendWith(OutputCaptureExtension.class)
class LoggingEmailSenderTests {

	private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
	private final Level originalLevel = logger.getLevel();

	@AfterEach
	void restoreLevel() {
		logger.setLevel(originalLevel);
	}

	@Test
	void DEBUG_로그를_켜도_받는_사람과_본문은_남기지_않는다(CapturedOutput output) {
		logger.setLevel(Level.DEBUG);

		new LoggingEmailSender().send("member@example.com", "[이음] 이메일 인증번호", "인증번호는 Ab3dE9입니다.");

		assertThat(output).contains("[이음] 이메일 인증번호").doesNotContain("member@example.com", "Ab3dE9");
	}
}
