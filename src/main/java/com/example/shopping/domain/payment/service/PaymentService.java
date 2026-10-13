package com.example.shopping.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.shopping.domain.order.dto.OrderDetailResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.service.OrderService;
import com.example.shopping.domain.order.service.OrderTxService;
import com.example.shopping.domain.payment.dto.PaymentConfirmRequest;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.gateway.PaymentGateway;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgRejectedException;
import com.example.shopping.domain.payment.gateway.PgUnavailableException;
import com.example.shopping.domain.payment.repository.PaymentRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

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
	private final OrderTxService orderTxService;
	private final PaymentRepository paymentRepository;
	private final PaymentGateway paymentGateway;

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
			throw new BusinessException(ErrorCode.PAYMENT_FAILED);
		}
		return orderService.getMyOrder(memberId, order.getOrderNo());
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

	private void cancelMismatchedPayment(Order order, PgPayment pg) {
		try {
			paymentGateway.cancel(pg.paymentKey(), "결제 금액 불일치", "mismatch-" + order.getOrderNo());
		} catch (PgRejectedException | PgUnavailableException e) {
			log.error("금액 불일치 결제 {}의 자동 환불 실패. 수동 환불이 필요합니다.", pg.paymentKey(), e);
		}
	}
}
