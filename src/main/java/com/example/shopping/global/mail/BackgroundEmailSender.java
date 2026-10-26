package com.example.shopping.global.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 응답을 메일 발송에 묶지 않을 때 쓴다. 예를 들어 비밀번호 재설정은 가입된 이메일인지와 상관없이 같은 속도로 응답해야
 * 응답 시간으로 가입 여부를 알아낼 수 없다. 발송에 실패하면 로그만 남긴다.
 */
@Component
public class BackgroundEmailSender implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(BackgroundEmailSender.class);

	private final EmailSender emailSender;
	private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

	public BackgroundEmailSender(EmailSender emailSender) {
		this.emailSender = emailSender;
		executor.setThreadNamePrefix("mail-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(200);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		executor.initialize();
	}

	public void send(String to, String subject, String text) {
		try {
			executor.execute(() -> {
				try {
					emailSender.send(to, subject, text);
				} catch (RuntimeException e) {
					log.error("메일을 보내지 못했습니다: to={}, subject={}", to, subject, e);
				}
			});
		} catch (TaskRejectedException e) {
			log.error("보낼 메일이 너무 많이 밀려 보내지 못했습니다: to={}, subject={}", to, subject, e);
		}
	}

	@Override
	public void destroy() {
		executor.shutdown();
	}
}
