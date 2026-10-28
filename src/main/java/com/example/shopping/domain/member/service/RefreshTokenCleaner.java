package com.example.shopping.domain.member.service;

import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.shopping.global.scheduling.ScheduledJobRunner;

import lombok.RequiredArgsConstructor;

/** 만료된 Refresh 토큰을 지운다. 하루에 한 번 크게 지우지 않고 1분마다 정해진 양씩 지운다. */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleaner {

	// 배포 중 이전 버전 서버와 같은 잠금을 쓰도록 주문 보정 스케줄러에 있을 때의 이름을 그대로 쓴다.
	private static final String JOB_NAME = "purgeExpiredRefreshTokens";
	private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private final RefreshTokenService refreshTokenService;
	private final ScheduledJobRunner jobRunner;

	@Scheduled(fixedDelay = 60_000)
	public void purgeExpiredRefreshTokens() {
		jobRunner.run(JOB_NAME, LOCK_DURATION, (stats, owner) -> stats.processed(refreshTokenService.purgeExpired()));
	}
}
