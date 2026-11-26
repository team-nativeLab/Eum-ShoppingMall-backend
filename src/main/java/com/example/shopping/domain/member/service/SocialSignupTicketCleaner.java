package com.example.shopping.domain.member.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.shopping.domain.member.repository.SocialSignupTicketRepository;
import com.example.shopping.global.scheduling.ScheduledJobRunner;

import lombok.RequiredArgsConstructor;

/** 지난 소셜 가입 대기를 지운다(#225). 제공자가 알려 준 이메일·이름이 들어 있어 10분이 지나면 남겨 둘 까닭이 없다. */
@Component
@RequiredArgsConstructor
public class SocialSignupTicketCleaner {

	private static final String JOB_NAME = "purgeSocialSignupTickets";
	private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

	private final SocialSignupTicketRepository ticketRepository;
	private final ScheduledJobRunner jobRunner;
	private final TransactionTemplate transactionTemplate;

	@Scheduled(cron = "0 25 * * * *")
	public void purgeSocialSignupTickets() {
		jobRunner.run(JOB_NAME, LOCK_DURATION, (stats, owner) -> stats.processed(purge(LocalDateTime.now())));
	}

	public int purge(LocalDateTime now) {
		Integer deleted = transactionTemplate.execute(status -> ticketRepository.deleteExpired(now));
		return deleted == null ? 0 : deleted;
	}
}
