package com.example.shopping.domain.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.shopping.domain.member.service.RefreshTokenService;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.payment.gateway.PaymentGateway;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주기적으로 도는 정리 작업. 서버가 여러 대여도 같은 주문을 두 번 처리하지 않는다
 * (실제 변경은 OrderTxService의 조건부 상태 변경을 통과한 한 서버만 수행한다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMaintenanceScheduler {

	private static final int BATCH_SIZE = 200;
	private static final int STUCK_AFTER_MINUTES = 5;

	private final OrderRepository orderRepository;
	private final OrderTxService orderTxService;
	private final PaymentGateway paymentGateway;
	private final RefreshTokenService refreshTokenService;

	/** 결제 기한이 지난 결제 대기 주문을 만료시키고 잡아 둔 재고를 되돌린다. */
	@Scheduled(fixedDelay = 60_000)
	public void expireUnpaidOrders() {
		List<Long> orderIds = orderRepository.findExpiredPendingIds(LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
		for (Long orderId : orderIds) {
			try {
				orderTxService.expire(orderId);
			} catch (RuntimeException e) {
				log.error("주문 {} 만료 처리 실패", orderId, e);
			}
		}
	}

	/**
	 * 결제 승인 도중 응답을 못 받았거나 서버가 죽어 "승인 중"에 멈춘 주문을 PG의 실제 상태에 맞춘다.
	 * PG에서 결제된 것이 확인되면 결제 완료로, 결제 기록이 없으면 결제 전으로 되돌린다(이후 기한이 지나면 만료된다).
	 */
	@Scheduled(fixedDelay = 300_000)
	public void reconcileStuckPayments() {
		LocalDateTime before = LocalDateTime.now().minusMinutes(STUCK_AFTER_MINUTES);
		for (Long orderId : orderRepository.findStuckProcessingIds(before, PageRequest.of(0, BATCH_SIZE))) {
			try {
				reconcile(orderId);
			} catch (PgUnavailableException e) {
				log.warn("주문 {} 결제 상태 확인 실패. 다음 주기에 다시 시도합니다: {}", orderId, e.getMessage());
			} catch (RuntimeException e) {
				log.error("주문 {} 결제 보정 실패", orderId, e);
			}
		}
	}

	@Scheduled(cron = "0 0 4 * * *")
	public void purgeExpiredRefreshTokens() {
		refreshTokenService.purgeExpired();
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
		orderTxService.completePayment(orderId, actual.get());
	}
}
