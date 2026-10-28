package com.example.shopping.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.shopping.domain.order.dto.response.OrderDetailResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.order.service.OrderService;
import com.example.shopping.domain.order.service.OrderTxService;
import com.example.shopping.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.shopping.domain.payment.dto.request.PaymentWebhookRequest;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.entity.PaymentStatus;
import com.example.shopping.domain.payment.gateway.PaymentGateway;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.example.shopping.domain.payment.gateway.PgRejectedException;
import com.example.shopping.domain.payment.gateway.PgUnavailableException;
import com.example.shopping.domain.payment.repository.PaymentRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.monitoring.ManualCheckAlerts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 승인. 원칙은 "금액은 서버 DB 기준, 결제 상태는 PG 조회 기준"이다.
 * PG 호출은 트랜잭션 밖에서 하고, 그 앞뒤의 상태 변경만 OrderTxService의 짧은 트랜잭션으로 반영한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final OrderService orderService;
	private final OrderRepository orderRepository;
	private final OrderTxService orderTxService;
	private final PaymentRepository paymentRepository;
	private final PaymentGateway paymentGateway;
	private final VirtualAccountReconciler virtualAccountReconciler;
	private final ManualCheckAlerts alerts;

	public OrderDetailResponse confirm(Long memberId, PaymentConfirmRequest request) {
		Order order = orderService.findOwned(memberId, request.orderId());

		// 결제 완료 화면을 새로고침해 같은 요청이 다시 온 경우: 다시 승인하지 않고 현재 결과를 돌려준다.
		Optional<Payment> processed = paymentRepository.findByPaymentKey(request.paymentKey());
		if (processed.isPresent()) {
			if (!processed.get().getOrderId().equals(order.getId())) {
				throw new BusinessException(ErrorCode.PAYMENT_FAILED);
			}
			return orderService.getMyOrder(memberId, order.getOrderNo());
		}

		if (order.getTotalAmount() != request.amount()) {
			log.warn("결제 금액 불일치(변조 의심): 주문 {} 서버 금액 {} 요청 금액 {}", order.getOrderNo(),
					order.getTotalAmount(), request.amount());
			throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}
		if (order.isExpired(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.ORDER_EXPIRED);
		}
		// 같은 주문에 승인 요청이 동시에 와도 한 요청만 통과한다.
		if (!orderTxService.startPayment(order.getId())) {
			throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
		}

		PgPayment pg = approve(order, request.paymentKey());

		// PG가 실제로 승인한 금액과 주문번호도 다시 대조한다. 다르면 바로 환불하고 실패 처리한다.
		boolean differentOrder = pg.orderNo() != null && !pg.orderNo().equals(order.getOrderNo());
		if (pg.totalAmount() != order.getTotalAmount() || differentOrder) {
			log.error("PG 승인 내용 불일치: 주문 {} 서버 금액 {} / PG 주문 {} PG 금액 {}", order.getOrderNo(),
					order.getTotalAmount(), pg.orderNo(), pg.totalAmount());
			cancelMismatchedPayment(order, pg);
			orderTxService.revertPayment(order.getId());
			throw new BusinessException(differentOrder ? ErrorCode.PAYMENT_FAILED : ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}
		if (!orderTxService.completePayment(order.getId(), pg)) {
			if (pg.status() == PgStatus.DONE) {
				return recoverUnappliedApproval(memberId, order, pg);
			}
			if (pg.status() == PgStatus.WAITING_FOR_DEPOSIT) {
				log.error("주문 {} 가상계좌 승인 응답에 계좌 또는 입금 기한 정보가 없어 보정 대기로 둡니다.", order.getOrderNo());
				throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
			}
			throw new BusinessException(ErrorCode.PAYMENT_FAILED);
		}
		return orderService.getMyOrder(memberId, order.getOrderNo());
	}

	/** Toss 웹훅은 신호로만 사용한다. 실제 결제 상태는 저장한 비밀값과 PG 조회 결과로 확인한다. */
	public void handleWebhook(PaymentWebhookRequest request) {
		boolean depositCallback = "DEPOSIT_CALLBACK".equals(request.eventType());
		if (!depositCallback && !"PAYMENT_STATUS_CHANGED".equals(request.eventType())) {
			return;
		}
		Optional<VirtualAccountPayment> verified = findVerifiedVirtualAccount(request.data());
		if (verified.isEmpty()) {
			// 입금 콜백은 발급 때 받은 비밀값을 담아 오므로 확인되지 않으면 거절한다. 상태 변경 알림은 다른 결제의 것일 수 있어 무시한다.
			if (depositCallback) {
				throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
			}
			return;
		}
		Order order = verified.get().order();
		Optional<PgPayment> actual = paymentGateway.findByOrderNo(order.getOrderNo());
		if (actual.isEmpty()) {
			alerts.raise("virtual_account_not_found", "주문 {} 가상계좌 웹훅을 받았으나 PG에서 결제를 찾지 못했습니다.",
					order.getOrderNo());
			return;
		}
		virtualAccountReconciler.reconcile(order, verified.get().payment(), actual.get());
	}

	/** 웹훅이 가리키는 주문의 최근 결제가 가상계좌이고 웹훅의 비밀값·결제 키가 맞을 때만 돌려준다. */
	private Optional<VirtualAccountPayment> findVerifiedVirtualAccount(PaymentWebhookRequest.Data data) {
		if (data == null || !data.hasVerificationFields()) {
			return Optional.empty();
		}
		return orderRepository.findByOrderNo(data.orderId())
				.flatMap(order -> paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId())
						.filter(Payment::isVirtualAccount)
						.filter(payment -> payment.matchesWebhookSecret(data.secret()))
						.filter(payment -> data.paymentKey() == null || payment.getPaymentKey().equals(data.paymentKey()))
						.map(payment -> new VirtualAccountPayment(order, payment)));
	}

	private PgPayment approve(Order order, String paymentKey) {
		try {
			return paymentGateway.confirm(paymentKey, order.getOrderNo(), order.getTotalAmount());
		} catch (PgRejectedException | PgUnavailableException e) {
			log.warn("주문 {} 결제 승인 요청 실패: {}", order.getOrderNo(), e.getMessage());

			// 실패로 단정하지 않고 PG에 실제 상태를 확인한다.
			// (승인은 됐는데 응답만 못 받았거나, 이미 승인된 결제를 다시 요청해 거절된 경우일 수 있다.)
			Optional<PgPayment> actual = findAtPg(order.getOrderNo());
			if (actual.isPresent() && actual.get().isApproved() && paymentKey.equals(actual.get().paymentKey())) {
				return actual.get();
			}
			if (e instanceof PgRejectedException) {
				orderTxService.revertPayment(order.getId());
				throw new BusinessException(ErrorCode.PAYMENT_FAILED);
			}
			// 결과를 알 수 없다. 주문은 "승인 중"으로 두고 보정 작업(OrderMaintenanceScheduler)이 마무리한다.
			throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
		}
	}

	private Optional<PgPayment> findAtPg(String orderNo) {
		try {
			return paymentGateway.findByOrderNo(orderNo);
		} catch (PgUnavailableException e) {
			return Optional.empty();
		}
	}

	/**
	 * PG는 승인했는데 주문을 결제 완료로 바꾸지 못한 경우. 승인을 오래 기다리는 동안 결제 보정 작업이 주문을
	 * 결제 전으로 되돌렸거나 같은 승인을 먼저 반영한 경우다.
	 * 이미 반영됐으면 성공으로, 결제 전으로 돌아갔으면 다시 반영한다. 둘 다 아니면(만료·취소 등) 고객이 돈만 내고
	 * 주문이 없는 일이 없도록 승인을 취소(환불)한다.
	 */
	private OrderDetailResponse recoverUnappliedApproval(Long memberId, Order order, PgPayment pg) {
		boolean alreadyApplied = paymentRepository.findByPaymentKey(pg.paymentKey())
				.filter(payment -> payment.getOrderId().equals(order.getId())
						&& payment.getStatus() == PaymentStatus.DONE)
				.isPresent();
		if (alreadyApplied) {
			return orderService.getMyOrder(memberId, order.getOrderNo());
		}
		if (orderTxService.startPayment(order.getId())) {
			if (orderTxService.completePayment(order.getId(), pg)) {
				return orderService.getMyOrder(memberId, order.getOrderNo());
			}
			// 결제 진행 중으로 남았으므로 결제 보정 작업이 PG 승인을 확인해 반영한다.
			log.error("주문 {} PG 승인을 다시 반영하지 못했습니다. 결제 보정 작업이 처리합니다.", order.getOrderNo());
			throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
		}
		try {
			PgPayment canceled = paymentGateway.cancel(pg.paymentKey(), "주문에 결제를 반영하지 못함",
					"unapplied-" + order.getOrderNo());
			if (canceled.status() == PgStatus.CANCELED) {
				log.warn("주문 {} 상태가 바뀌어 반영하지 못한 PG 승인을 취소했습니다.", order.getOrderNo());
				throw new BusinessException(ErrorCode.PAYMENT_FAILED);
			}
		} catch (PgRejectedException | PgUnavailableException e) {
			log.error("주문 {} 반영하지 못한 PG 승인의 자동 취소 실패", order.getOrderNo(), e);
		}
		alerts.raise("unapplied_approval", "주문 {} PG 승인을 주문에 반영하지도 취소하지도 못했습니다: paymentKey={}",
				order.getOrderNo(), pg.paymentKey());
		throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
	}

	private void cancelMismatchedPayment(Order order, PgPayment pg) {
		try {
			paymentGateway.cancel(pg.paymentKey(), "결제 금액 불일치", "mismatch-" + order.getOrderNo());
		} catch (PgRejectedException | PgUnavailableException e) {
			alerts.raise("refund_failed", "금액 불일치 결제 {}를 자동 환불하지 못해 수동 환불이 필요합니다.", pg.paymentKey(), e);
		}
	}

	private record VirtualAccountPayment(Order order, Payment payment) {
	}
}
