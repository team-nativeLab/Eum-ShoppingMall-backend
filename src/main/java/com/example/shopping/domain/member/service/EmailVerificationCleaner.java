package com.example.shopping.domain.member.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.shopping.domain.member.repository.EmailVerificationRepository;
import com.example.shopping.global.scheduling.ScheduledJobRunner;

import lombok.RequiredArgsConstructor;

/** 하루가 지난 이메일 인증번호 기록을 지운다. 인증번호는 10분, 확인 토큰은 30분이면 만료되므로 더 쓸 일이 없다. */
@Component
@RequiredArgsConstructor
public class EmailVerificationCleaner {

	private static final String JOB_NAME = "purgeEmailVerifications";
	private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
	private static final Duration KEEP_FOR = Duration.ofDays(1);

	private final EmailVerificationRepository verificationRepository;
	private final ScheduledJobRunner jobRunner;
	private final TransactionTemplate transactionTemplate;

	@Scheduled(cron = "0 15 * * * *")
	public void purgeEmailVerifications() {
		jobRunner.run(JOB_NAME, LOCK_DURATION, (stats, owner) -> stats.processed(purge(LocalDateTime.now().minus(KEEP_FOR))));
	}

	public int purge(LocalDateTime before) {
		Integer deleted = transactionTemplate.execute(status -> verificationRepository.deleteCreatedBefore(before));
		return deleted == null ? 0 : deleted;
	}
}
