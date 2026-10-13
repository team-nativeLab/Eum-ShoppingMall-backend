package com.example.shopping.domain.order.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.shopping.domain.order.dto.OrderDetailResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
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

/**
 * 고객의 주문 취소(전액 환불). 순서는 "주문을 취소 상태로 먼저 잡는다 → PG 환불 → 결제·재고 반영"이다.
 * 주문을 먼저 잡아 두는 이유는 환불하는 사이에 관리자가 배송을 시작해 "환불됐는데 발송되는" 일을 막기 위해서다.
 * 환불이 실패하면 주문을 원래 상태로 되돌린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelService {

	// 배송이 시작되기 전까지만 취소할 수 있다.
	private static final Set<OrderStatus> CANCELABLE = Set.of(OrderStatus.PAID, OrderStatus.PREPARING);

	private final OrderService orderService;
	private final OrderTxService orderTxService;
	private final PaymentRepository paymentRepository;
	private final PaymentGateway paymentGateway;

	public OrderDetailResponse cancel(Long memberId, String orderNo) {
		Order order = orderService.findOwned(memberId, orderNo);
		OrderStatus previous = order.getStatus();
		Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId())
				.filter(p -> p.getStatus() == PaymentStatus.DONE)
				.orElse(null);
		if (!CANCELABLE.contains(previous) || payment == null
				|| !orderTxService.claimCancel(order.getId(), previous)) {
			throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
		}

		if (!refund(order, payment)) {
			orderTxService.revertCancel(order.getId(), previous);
			throw new BusinessException(ErrorCode.CANCEL_FAILED);
		}
		orderTxService.completeCancel(order.getId(), payment.getId());
		return orderService.getMyOrder(memberId, orderNo);
	}

	/** @return PG에서 환불된 것이 확인되면 true */
	private boolean refund(Order order, Payment payment) {
		try {
			paymentGateway.cancel(payment.getPaymentKey(), "고객 주문 취소", "cancel-" + order.getOrderNo());
			return true;
		} catch (PgRejectedException | PgUnavailableException e) {
			log.warn("주문 {} 환불 요청 실패: {}", order.getOrderNo(), e.getMessage());
			// 응답만 못 받았을 뿐 실제로는 환불됐을 수 있으므로 PG에 한 번 더 확인한다.
			return isCanceledAtPg(order.getOrderNo());
		}
	}

	private boolean isCanceledAtPg(String orderNo) {
		try {
			Optional<PgPayment> actual = paymentGateway.findByOrderNo(orderNo);
			return actual.isPresent() && actual.get().status() == PgStatus.CANCELED;
		} catch (PgUnavailableException e) {
			return false;
		}
	}
}
