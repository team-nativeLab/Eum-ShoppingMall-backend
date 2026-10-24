package com.example.shopping.domain.order.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.shopping.domain.order.dto.response.OrderDetailResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.order.repository.OrderRepository;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 고객 주문 취소와 취소 결과 보정. PG 통신은 DB 트랜잭션 밖에서 수행한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelService {

	private static final Set<OrderStatus> CANCELABLE = Set.of(OrderStatus.PAID, OrderStatus.PREPARING);
	private static final String CUSTOMER_CANCEL_REASON = "고객 주문 취소";
	private static final int IDEMPOTENCY_RETRY_CUTOFF_DAYS = 14;

	private final OrderService orderService;
	private final OrderRepository orderRepository;
	private final OrderTxService orderTxService;
	private final PaymentRepository paymentRepository;
	private final PaymentGateway paymentGateway;

	public OrderDetailResponse cancel(Long memberId, String orderNo) {
		Order order = orderService.findOwned(memberId, orderNo);
		Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId())
				.filter(p -> p.getStatus() == PaymentStatus.DONE)
				.orElse(null);
		cancelPaidOrder(order, payment, CUSTOMER_CANCEL_REASON, memberId);
		return orderService.getMyOrder(memberId, orderNo);
	}

	/** 관리자 주문 취소. 결제 전 주문은 바로 만료하고, 가상계좌 대기 주문은 PG 취소 확인 뒤 만료한다. */
	public void cancelByAdmin(Long orderId, Long adminId, String reason) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		OrderStatus previous = order.getStatus();
		String cancelKey = newCancelIdempotencyKey();
		if (previous == OrderStatus.PENDING) {
			if (!orderTxService.claimCancel(orderId, previous, cancelKey, reason, adminId)) {
				throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
			}
			orderTxService.completeUnpaidAdminCancellation(orderId, null);
			return;
		}
		if (previous == OrderStatus.WAITING_DEPOSIT) {
			Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
					.filter(p -> p.getStatus() == PaymentStatus.WAITING_DEPOSIT)
					.orElseThrow(() -> new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED));
			if (!orderTxService.claimCancel(orderId, previous, cancelKey, reason, adminId)) {
				throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
			}
			try {
				PgPayment result = paymentGateway.cancel(payment.getPaymentKey(), reason, cancelKey);
				if (isFullyCanceled(order, payment, result)) {
					orderTxService.completeUnpaidAdminCancellation(orderId, payment.getId());
					return;
				}
				throw pendingCancellation(order.getOrderNo());
			} catch (PgRejectedException e) {
				if (isCanceledAtPg(order, payment)) {
					orderTxService.completeUnpaidAdminCancellation(orderId, payment.getId());
					return;
				}
				orderTxService.revertCancel(orderId, previous);
				throw new BusinessException(ErrorCode.CANCEL_FAILED);
			} catch (PgUnavailableException e) {
				if (isCanceledAtPg(order, payment)) {
					orderTxService.completeUnpaidAdminCancellation(orderId, payment.getId());
					return;
				}
				throw pendingCancellation(order.getOrderNo());
			}
		}
		if (!CANCELABLE.contains(previous)) {
			throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
		}
		Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
				.filter(p -> p.getStatus() == PaymentStatus.DONE)
				.orElse(null);
		cancelPaidOrder(order, payment, reason, adminId);
	}

	private void cancelPaidOrder(Order order, Payment payment, String reason, Long actorId) {
		OrderStatus previous = order.getStatus();
		String cancelKey = newCancelIdempotencyKey();
		if (!CANCELABLE.contains(previous) || payment == null
				|| !orderTxService.claimCancel(order.getId(), previous, cancelKey, reason, actorId)) {
			throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
		}
		try {
			PgPayment result = paymentGateway.cancel(payment.getPaymentKey(), reason, cancelKey);
			if (isFullyCanceled(order, payment, result)) {
				orderTxService.completeCancel(order.getId(), payment.getId());
				return;
			}
			log.error("주문 {} PG 취소 응답이 전액 취소 상태가 아닙니다: status={}, balance={}",
					order.getOrderNo(), result.status(), result.balanceAmount());
			throw pendingCancellation(order.getOrderNo());
		} catch (PgRejectedException e) {
			// Toss의 명확한 4xx 거절은 취소 미처리를 뜻한다. 조회가 가능하면 이전 요청의 취소 여부도 우선 대조한다.
			if (isCanceledAtPg(order, payment)) {
				orderTxService.completeCancel(order.getId(), payment.getId());
				return;
			}
			orderTxService.revertCancel(order.getId(), previous);
			log.warn("주문 {} 환불 요청이 PG에서 거절되었습니다: {}", order.getOrderNo(), e.getMessage());
			throw new BusinessException(ErrorCode.CANCEL_FAILED);
		} catch (PgUnavailableException e) {
			log.warn("주문 {} 환불 결과를 확인하지 못했습니다. 취소 보정 작업에서 재시도합니다: {}",
					order.getOrderNo(), e.getMessage());
			if (isCanceledAtPg(order, payment)) {
				orderTxService.completeCancel(order.getId(), payment.getId());
				return;
			}
			throw pendingCancellation(order.getOrderNo());
		}
	}

	/** 취소 중 멈춘 주문과 과거 CANCELED/DONE 불일치 주문을 보정한다. */
	public void reconcileCancellation(Long orderId) {
		LocalDateTime now = LocalDateTime.now();
		if (!orderTxService.claimCancellationRecovery(orderId, now)) {
			return;
		}
		Order order = orderRepository.findById(orderId).orElse(null);
		if (order == null) {
			return;
		}
		Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId).orElse(null);
		if (payment == null || (payment.getStatus() != PaymentStatus.DONE
				&& payment.getStatus() != PaymentStatus.WAITING_DEPOSIT)) {
			if (order.getStatus() == OrderStatus.CANCEL_REQUESTED) {
				log.error("주문 {} 취소 보정에 필요한 완료 결제 기록이 없습니다. 수동 확인이 필요합니다.", order.getOrderNo());
			}
			return;
		}

		Optional<PgPayment> actual;
		try {
			actual = paymentGateway.findByOrderNo(order.getOrderNo());
		} catch (PgUnavailableException e) {
			log.warn("주문 {} PG 취소 상태 조회 실패. 다음 주기에 재시도합니다: {}", order.getOrderNo(), e.getMessage());
			return;
		}

		if (actual.isPresent() && isFullyCanceled(order, payment, actual.get())) {
			completeConfirmedCancellation(order, payment);
			return;
		}
		if (actual.isEmpty() || !isUncanceledPayment(order, actual.get(), payment)) {
			log.error("주문 {} PG 상태가 취소 보정 조건과 일치하지 않습니다. 수동 확인이 필요합니다.", order.getOrderNo());
			return;
		}
		LocalDateTime cancelRequestedAt = order.getCancelRequestedAt() == null
				? order.getUpdatedAt() : order.getCancelRequestedAt();
		if (cancelRequestedAt.isBefore(now.minusDays(IDEMPOTENCY_RETRY_CUTOFF_DAYS))) {
			log.error("주문 {} 취소 요청이 멱등 키 15일 유효기간에 근접했습니다. 자동 재요청을 중단하고 수동 확인이 필요합니다.",
					order.getOrderNo());
			return;
		}

		// 처음 요청과 같은 키를 사용한다. Toss 멱등 키는 최초 요청 후 15일 동안 유효하다.
		try {
			PgPayment result = paymentGateway.cancel(payment.getPaymentKey(), cancellationReason(order),
					recoveryIdempotencyKey(order));
			if (isFullyCanceled(order, payment, result)) {
				completeConfirmedCancellation(order, payment);
			} else {
				log.error("주문 {} 재취소 응답이 전액 취소 상태가 아닙니다: status={}, balance={}",
						order.getOrderNo(), result.status(), result.balanceAmount());
			}
		} catch (PgRejectedException e) {
			if (isCanceledAtPg(order, payment)) {
				completeConfirmedCancellation(order, payment);
				return;
			}
			if (order.getStatus() == OrderStatus.CANCEL_REQUESTED && order.getCancelPreviousStatus() != null) {
				orderTxService.revertCancel(orderId, order.getCancelPreviousStatus());
				log.warn("주문 {} PG가 취소를 확실히 거절해 이전 주문 상태로 복구했습니다: {}",
						order.getOrderNo(), e.getMessage());
			} else {
				log.error("과거 주문 {} PG가 취소 재요청을 거절했습니다. 수동 확인이 필요합니다: {}",
						order.getOrderNo(), e.getMessage());
			}
		} catch (PgUnavailableException e) {
			log.warn("주문 {} 재취소 결과를 확인하지 못했습니다. 다음 주기에 재시도합니다: {}",
					order.getOrderNo(), e.getMessage());
		}
	}

	private void completeConfirmedCancellation(Order order, Payment payment) {
		if (payment.getStatus() == PaymentStatus.WAITING_DEPOSIT) {
			orderTxService.completeUnpaidAdminCancellation(order.getId(), payment.getId());
		} else {
			orderTxService.completeCancel(order.getId(), payment.getId());
		}
	}

	private boolean isCanceledAtPg(Order order, Payment payment) {
		try {
			return paymentGateway.findByOrderNo(order.getOrderNo())
					.filter(pg -> isFullyCanceled(order, payment, pg))
					.isPresent();
		} catch (PgUnavailableException e) {
			log.warn("주문 {} 환불 상태를 조회하지 못했습니다: {}", order.getOrderNo(), e.getMessage());
			return false;
		}
	}

	private boolean isFullyCanceled(Order order, Payment payment, PgPayment pg) {
		return pg.status() == PgStatus.CANCELED && pg.balanceAmount() == 0
				&& pg.totalAmount() == order.getTotalAmount()
				&& order.getOrderNo().equals(pg.orderNo())
				&& payment.getPaymentKey().equals(pg.paymentKey());
	}

	private boolean isUncanceledPayment(Order order, PgPayment pg, Payment payment) {
		PgStatus expectedStatus = payment.getStatus() == PaymentStatus.WAITING_DEPOSIT
				? PgStatus.WAITING_FOR_DEPOSIT : PgStatus.DONE;
		return pg.status() == expectedStatus && pg.balanceAmount() == order.getTotalAmount()
				&& pg.totalAmount() == order.getTotalAmount()
				&& order.getOrderNo().equals(pg.orderNo())
				&& payment.getPaymentKey().equals(pg.paymentKey());
	}

	private String newCancelIdempotencyKey() {
		return "cancel-" + UUID.randomUUID();
	}

	private String recoveryIdempotencyKey(Order order) {
		// 기존 장애 주문은 과거 취소 요청에 사용했던 키로 중복 환불을 막는다.
		return order.getCancelIdempotencyKey() == null
				? "cancel-" + order.getOrderNo() : order.getCancelIdempotencyKey();
	}

	private String cancellationReason(Order order) {
		return order.getCancelReason() == null ? CUSTOMER_CANCEL_REASON : order.getCancelReason();
	}

	private BusinessException pendingCancellation(String orderNo) {
		log.error("주문 {} 환불 상태 미확정. 주문은 배송 불가 상태로 유지합니다.", orderNo);
		return new BusinessException(ErrorCode.CANCEL_PENDING_CONFIRMATION);
	}
}
