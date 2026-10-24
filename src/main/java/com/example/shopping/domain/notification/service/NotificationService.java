package com.example.shopping.domain.notification.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.shopping.domain.notification.entity.NotificationChannel;
import com.example.shopping.domain.notification.entity.NotificationMessage;
import com.example.shopping.domain.notification.entity.NotificationStatus;
import com.example.shopping.domain.notification.repository.NotificationMessageRepository;
import com.example.shopping.domain.notification.sender.AlimtalkSender;
import com.example.shopping.global.scheduling.SchedulerLockService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고객 알림(알림톡)을 기록하고 보낸다.
 * 보낼 일이 생긴 트랜잭션에서 기록하고(enqueue), 커밋된 뒤 따로 보낸다. 알림이 실패하거나 대행사가 응답하지 않아도
 * 답변 등록 같은 원래 작업은 영향을 받지 않는다. 실패한 알림은 1분마다 도는 작업이 다시 보낸다(최대 3번).
 */
@Slf4j
@Service
public class NotificationService implements DisposableBean {

	private static final String RETRY_JOB = "dispatchNotifications";
	private static final Duration RETRY_LOCK = Duration.ofMinutes(5);
	private static final Duration STUCK_AFTER = Duration.ofMinutes(10);
	private static final int RETRY_BATCH = 50;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final NotificationMessageRepository repository;
	private final AlimtalkSender alimtalkSender;
	private final TransactionTemplate transactionTemplate;
	private final SchedulerLockService schedulerLockService;
	private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

	public NotificationService(NotificationMessageRepository repository, AlimtalkSender alimtalkSender,
			TransactionTemplate transactionTemplate, SchedulerLockService schedulerLockService) {
		this.repository = repository;
		this.alimtalkSender = alimtalkSender;
		this.transactionTemplate = transactionTemplate;
		this.schedulerLockService = schedulerLockService;
		executor.setThreadNamePrefix("notification-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(500);
		executor.initialize();
	}

	/**
	 * 알림톡을 보낼 것으로 기록한다. 호출한 트랜잭션이 커밋되면 바로 보내고, 롤백되면 기록도 보내기도 하지 않는다.
	 *
	 * @return 기록한 알림
	 */
	public NotificationMessage enqueueAlimtalk(String phoneNumber, String templateCode, Map<String, String> variables,
			String referenceType, Long referenceId) {
		NotificationMessage message = repository.save(NotificationMessage.create(NotificationChannel.ALIMTALK,
				templateCode, phoneNumber, JSON.writeValueAsString(variables), referenceType, referenceId));
		Long id = message.getId();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					dispatchLater(id);
				}
			});
		} else {
			dispatchLater(id);
		}
		return message;
	}

	/** 같은 대상(예: 문의 답변)으로 보낸 가장 최근 알림 */
	public Optional<NotificationMessage> findLatest(String referenceType, Long referenceId) {
		return repository.findByReferenceTypeAndReferenceIdOrderByIdDesc(referenceType, referenceId).stream()
				.findFirst();
	}

	/** 보낼 차례가 된 알림(실패 후 다시 보낼 알림 포함)을 보낸다. */
	@Scheduled(fixedDelay = 60_000)
	public void retryDueNotifications() {
		Optional<String> owner = schedulerLockService.tryAcquire(RETRY_JOB, RETRY_LOCK);
		if (owner.isEmpty()) {
			return;
		}
		try {
			int processed = dispatchDue(LocalDateTime.now());
			if (processed > 0) {
				log.info("정기 작업 {} 완료: 처리 {}", RETRY_JOB, processed);
			}
		} catch (RuntimeException e) {
			log.error("정기 작업 {} 실행 실패", RETRY_JOB, e);
		} finally {
			schedulerLockService.release(RETRY_JOB, owner.get());
		}
	}

	/** @return 보내려고 시도한 알림 수 */
	public int dispatchDue(LocalDateTime now) {
		transactionTemplate.executeWithoutResult(status -> repository.releaseStuck(now.minus(STUCK_AFTER),
				NotificationStatus.PENDING, NotificationStatus.SENDING));
		List<Long> ids = repository.findDueIds(now, PageRequest.of(0, RETRY_BATCH));
		ids.forEach(this::dispatch);
		return ids.size();
	}

	/**
	 * 알림 한 건을 보낸다. 다른 서버가 이미 가져갔으면 아무것도 하지 않는다.
	 * 대행사 호출은 트랜잭션 밖에서 해 DB 커넥션을 잡고 기다리지 않는다.
	 */
	public void dispatch(Long id) {
		LocalDateTime now = LocalDateTime.now();
		Integer claimed = transactionTemplate.execute(status -> repository.claim(id, now, NotificationStatus.PENDING,
				NotificationStatus.SENDING));
		if (claimed == null || claimed == 0) {
			return;
		}
		NotificationMessage message = repository.findById(id).orElseThrow();
		String error = null;
		boolean skipped = false;
		if (!alimtalkSender.isConfigured()) {
			skipped = true;
		} else {
			try {
				alimtalkSender.send(message.getRecipient(), message.getTemplateCode(),
						JSON.readValue(message.getVariables(), new TypeReference<Map<String, String>>() {
						}));
			} catch (RuntimeException e) {
				error = e.getClass().getSimpleName() + ": " + e.getMessage();
				log.warn("알림톡을 보내지 못했습니다: id={}, 시도 {}회", id, message.getAttempts() + 1, e);
			}
		}
		boolean wasSkipped = skipped;
		String failure = error;
		transactionTemplate.executeWithoutResult(status -> {
			NotificationMessage current = repository.findById(id).orElseThrow();
			if (wasSkipped) {
				current.markSkipped("알림톡 대행사가 설정되지 않아 보내지 않았습니다.");
			} else if (failure == null) {
				current.markSent(LocalDateTime.now());
			} else {
				current.markFailed(failure, LocalDateTime.now());
				if (current.getStatus() == NotificationStatus.FAILED) {
					log.error("알림톡을 {}번 모두 보내지 못했습니다. 확인이 필요합니다: id={}, 대상={} {}",
							NotificationMessage.MAX_ATTEMPTS, id, current.getReferenceType(), current.getReferenceId());
				}
			}
		});
	}

	private void dispatchLater(Long id) {
		try {
			executor.execute(() -> {
				try {
					dispatch(id);
				} catch (RuntimeException e) {
					// 기록은 PENDING으로 남아 있으므로 정기 작업이 다시 보낸다.
					log.error("알림 발송 처리 실패: id={}", id, e);
				}
			});
		} catch (TaskRejectedException e) {
			log.warn("보낼 알림이 많이 밀려 정기 작업에서 보냅니다: id={}", id);
		}
	}

	@Override
	public void destroy() {
		executor.shutdown();
	}
}
