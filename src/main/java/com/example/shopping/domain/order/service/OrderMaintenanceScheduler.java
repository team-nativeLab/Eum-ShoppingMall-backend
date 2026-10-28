package com.example.shopping.domain.order.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.entity.PaymentStatus;
import com.example.shopping.domain.payment.gateway.PaymentGateway;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.example.shopping.domain.payment.gateway.PgUnavailableException;
import com.example.shopping.domain.payment.repository.PaymentRepository;
import com.example.shopping.global.scheduling.ScheduledJobRunner;
import com.example.shopping.global.scheduling.SchedulerLockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Scheduled maintenance is serialized across application instances by database leases. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMaintenanceScheduler {

	private static final int DB_BATCH_SIZE = 50;
	private static final int PG_BATCH_SIZE = 10;
	private static final int STUCK_AFTER_MINUTES = 5;
	private static final Duration JOB_TIME_LIMIT = Duration.ofMinutes(8);
	// PG calls have 2s connect and 10s read timeouts; this lease covers bounded work plus DB overhead.
	private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private final OrderRepository orderRepository;
	private final OrderTxService orderTxService;
	private final OrderCancelService orderCancelService;
	private final PaymentRepository paymentRepository;
	private final PaymentGateway paymentGateway;
	private final SchedulerLockService schedulerLockService;
	private final ScheduledJobRunner jobRunner;

	/**
	 * 결제 기한이 지난 결제 대기 주문을 만료시키고 잡아 둔 재고를 되돌린다.
	 * DB만 쓰는 작업이라 자기 잠금으로 돌린다. PG를 부르는 가상계좌 작업이 느려도 재고 반환이 늦어지지 않는다.
	 */
	@Scheduled(fixedDelay = 60_000)
	public void expireUnpaidOrders() {
		jobRunner.run("expireUnpaidOrders", LOCK_DURATION, (stats, owner) -> {
			List<Long> pendingIds = orderRepository.findExpiredPendingIds(LocalDateTime.now(),
					PageRequest.of(0, DB_BATCH_SIZE));
			for (Long orderId : pendingIds) {
				if (stats.timeLimitReached(JOB_TIME_LIMIT)) {
					break;
				}
				process(stats, "주문 만료 처리", orderId, () -> orderTxService.expire(orderId));
			}
		});
	}

	/**
	 * 입금 기한이 지난 가상계좌를 PG에서 확인해 입금 완료나 만료로 정리한다.
	 * 같은 가상계좌를 동시에 보정하지 않도록 가상계좌 상태 보정과 같은 잠금을 쓴다.
	 */
	@Scheduled(fixedDelay = 60_000)
	public void reconcileExpiredDeposits() {
		jobRunner.run("reconcileExpiredDeposits", "reconcileWaitingDeposits", LOCK_DURATION, (stats, owner) -> {
			List<Long> expiredDepositIds = orderRepository.findExpiredWaitingDepositIds(LocalDateTime.now(),
					PageRequest.of(0, PG_BATCH_SIZE));
			for (Long orderId : expiredDepositIds) {
				if (stats.timeLimitReached(JOB_TIME_LIMIT)) {
					break;
				}
				process(stats, "가상계좌 만료 보정", orderId, () -> reconcileVirtualAccount(orderId));
			}
		});
	}

	/** 결제 승인 도중 응답을 못 받은 주문을 PG의 실제 상태에 맞춘다. */
	@Scheduled(fixedDelay = 300_000)
	public void reconcileStuckPayments() {
		jobRunner.run("reconcileStuckPayments", LOCK_DURATION, (stats, owner) -> {
			LocalDateTime before = LocalDateTime.now().minusMinutes(STUCK_AFTER_MINUTES);
			List<Long> orderIds = orderRepository.findStuckProcessingIds(before, PageRequest.of(0, PG_BATCH_SIZE));
			for (Long orderId : orderIds) {
				if (stats.timeLimitReached(JOB_TIME_LIMIT)) {
					break;
				}
				process(stats, "결제 상태 보정", orderId, () -> reconcile(orderId));
			}
		});
	}

	/** PG 응답 누락으로 멈춘 취소와 과거 CANCELED/DONE 불일치를 복구한다. */
	@Scheduled(fixedDelay = 300_000)
	public void reconcileStuckCancellations() {
		jobRunner.run("reconcileStuckCancellations", LOCK_DURATION, (stats, owner) -> {
			LocalDateTime before = LocalDateTime.now().minusMinutes(STUCK_AFTER_MINUTES);
			List<Long> orderIds = orderRepository.findStuckCancellationIds(before, PageRequest.of(0, PG_BATCH_SIZE));
			for (Long orderId : orderIds) {
				if (stats.timeLimitReached(JOB_TIME_LIMIT)) {
					break;
				}
				process(stats, "취소 상태 보정", orderId, () -> orderCancelService.reconcileCancellation(orderId));
			}
		});
	}

	private void reconcile(Long orderId) {
		Order order = orderRepository.findById(orderId).orElseThrow();
		Optional<PgPayment> actual = paymentGateway.findByOrderNo(order.getOrderNo());

		if (actual.isEmpty() || !actual.get().isApproved()) {
			orderTxService.revertPayment(orderId);
			return;
		}
		if (actual.get().totalAmount() != order.getTotalAmount()) {
			// 자동으로 판단하지 않고 사람이 확인하도록 남겨 둔다.
			log.error("주문 {} PG 승인 금액 불일치(서버 {} / PG {}). 수동 확인이 필요합니다.", order.getOrderNo(),
					order.getTotalAmount(), actual.get().totalAmount());
			return;
		}
		if (!orderTxService.completePayment(orderId, actual.get())
				&& actual.get().status() == PgStatus.WAITING_FOR_DEPOSIT) {
			log.error("주문 {} 가상계좌 정보가 부족해 승인 보정을 보류합니다.", order.getOrderNo());
		}
	}

	/** Each instance resumes from a shared cursor so a capped batch does not starve later deposits. */
	@Scheduled(fixedDelay = 300_000)
	public void reconcileWaitingDeposits() {
		jobRunner.run("reconcileWaitingDeposits", LOCK_DURATION, this::reconcileWaitingDepositBatch);
	}

	private void reconcileWaitingDepositBatch(ScheduledJobRunner.JobStats stats, String owner) {
		String jobName = "reconcileWaitingDeposits";
		long cursor = schedulerLockService.getCursor(jobName, owner);
		List<Long> orderIds = orderRepository.findWaitingDepositIdsAfter(cursor, PageRequest.of(0, PG_BATCH_SIZE));
		if (orderIds.isEmpty()) {
			if (cursor != 0) {
				schedulerLockService.advanceCursor(jobName, owner, 0);
			}
			return;
		}

		int processedBeforeBatch = stats.processedCount();
		for (Long orderId : orderIds) {
			if (stats.timeLimitReached(JOB_TIME_LIMIT)) {
				break;
			}
			process(stats, "가상계좌 상태 보정", orderId, () -> reconcileVirtualAccount(orderId));
			cursor = orderId;
			if (!schedulerLockService.advanceCursor(jobName, owner, cursor)) {
				throw new IllegalStateException("Scheduler lease lost while advancing " + jobName);
			}
		}
		if (stats.processedCount() - processedBeforeBatch == orderIds.size() && orderIds.size() < PG_BATCH_SIZE) {
			schedulerLockService.advanceCursor(jobName, owner, 0);
		}
	}

	private void process(ScheduledJobRunner.JobStats stats, String operation, Long orderId, Runnable action) {
		try {
			action.run();
			stats.processed();
		} catch (PgUnavailableException e) {
			stats.failed();
			log.warn("주문 {} {} 실패. 다음 주기에 다시 시도합니다: {}", orderId, operation, e.getMessage());
		} catch (RuntimeException e) {
			stats.failed();
			log.error("주문 {} {} 실패", orderId, operation, e);
		}
	}

	private void reconcileVirtualAccount(Long orderId) {
		Order order = orderRepository.findById(orderId).orElse(null);
		Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
				.filter(p -> p.getStatus() == PaymentStatus.WAITING_DEPOSIT)
				.orElse(null);
		if (order == null || payment == null) {
			return;
		}

		Optional<PgPayment> actual = paymentGateway.findByOrderNo(order.getOrderNo());
		if (actual.isEmpty() || !matchesVirtualAccount(order, payment, actual.get())) {
			log.error("주문 {} 가상계좌 PG 정보 불일치. 수동 확인이 필요합니다.", order.getOrderNo());
			return;
		}

		PgPayment pg = actual.get();
		if (pg.status() == PgStatus.DONE) {
			orderTxService.completeVirtualAccountDeposit(orderId, payment.getId(), pg);
			return;
		}
		if ((pg.status() == PgStatus.WAITING_FOR_DEPOSIT || pg.status() == PgStatus.CANCELED)
				&& !payment.getVirtualAccountDueAt().isAfter(LocalDateTime.now())) {
			orderTxService.expireVirtualAccount(orderId, payment.getId());
		}
	}

	private boolean matchesVirtualAccount(Order order, Payment payment, PgPayment pg) {
		return "가상계좌".equals(payment.getMethod())
				&& payment.getVirtualAccountDueAt() != null
				&& payment.getVirtualAccountNumber() != null
				&& order.getOrderNo().equals(pg.orderNo())
				&& payment.getPaymentKey().equals(pg.paymentKey())
				&& pg.totalAmount() == order.getTotalAmount()
				&& payment.matchesWebhookSecret(pg.secret());
	}
}
