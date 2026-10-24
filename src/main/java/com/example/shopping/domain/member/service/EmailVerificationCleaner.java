package com.example.shopping.domain.member.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.shopping.domain.member.repository.EmailVerificationRepository;
import com.example.shopping.global.scheduling.SchedulerLockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 하루가 지난 이메일 인증번호 기록을 지운다. 인증번호는 10분, 확인 토큰은 30분이면 만료되므로 더 쓸 일이 없다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationCleaner {

	private static final String JOB_NAME = "purgeEmailVerifications";
	private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
	private static final Duration KEEP_FOR = Duration.ofDays(1);

	private final EmailVerificationRepository verificationRepository;
	private final SchedulerLockService schedulerLockService;
	private final TransactionTemplate transactionTemplate;

	@Scheduled(cron = "0 15 * * * *")
	public void purgeEmailVerifications() {
		Optional<String> owner = schedulerLockService.tryAcquire(JOB_NAME, LOCK_DURATION);
		if (owner.isEmpty()) {
			return;
		}
		try {
			int deleted = purge(LocalDateTime.now().minus(KEEP_FOR));
			log.info("정기 작업 {} 완료: 삭제 {}", JOB_NAME, deleted);
		} catch (RuntimeException e) {
			log.error("정기 작업 {} 실행 실패", JOB_NAME, e);
		} finally {
			schedulerLockService.release(JOB_NAME, owner.get());
		}
	}

	public int purge(LocalDateTime before) {
		Integer deleted = transactionTemplate.execute(status -> verificationRepository.deleteCreatedBefore(before));
		return deleted == null ? 0 : deleted;
	}
}
