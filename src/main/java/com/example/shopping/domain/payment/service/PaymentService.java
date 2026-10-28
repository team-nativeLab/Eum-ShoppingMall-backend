package com.example.shopping.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.shopping.domain.order.dto.response.OrderDetailResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
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
			if (pg.status() == PgStatus.WAITING_FOR_DEPOSIT) {
				log.error("주문 {} 가상계좌 승인 응답에 계좌 또는 입금 기한 정보가 없어 보정 대기로 둡니다.", order.getOrderNo());
				throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
			}
			if (pg.status() == PgStatus.DONE) {
				return recoverUnappliedApproval(memberId, order, pg);
			}
			throw new BusinessException(ErrorCode.PAYMENT_FAILED);
		}
		return orderService.getMyOrder(memberId, order.getOrderNo());
	}

	/** Toss 웹훅은 신호로만 사용한다. 실제 결제 상태는 로컬 비밀값과 PG 조회 결과로 확인한다. */
	public void handleWebhook(PaymentWebhookRequest request) {
		if (!"DEPOSIT_CALLBACK".equals(request.eventType())
				&& !"PAYMENT_STATUS_CHANGED".equals(request.eventType())) {
			return;
		}
		boolean depositCallback = "DEPOSIT_CALLBACK".equals(request.eventType());
		PaymentWebhookRequest.Data data = request.data();
		if (data == null || !data.hasVerificationFields()) {
			if (depositCallback) {
				throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
			}
			return;
		}
		Optional<Order> orderResult = orderRepository.findByOrderNo(data.orderId());
		if (orderResult.isEmpty()) {
			if (depositCallback) {
				throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
			}
			return;
		}
		Order order = orderResult.get();
		Optional<Payment> paymentResult = paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId());
		if (paymentResult.isEmpty() || !"가상계좌".equals(paymentResult.get().getMethod())) {
			if (depositCallback) {
				throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
			}
			return;
		}
		Payment payment = paymentResult.get();
		if (!payment.matchesWebhookSecret(data.secret())
				|| (data.paymentKey() != null && !payment.getPaymentKey().equals(data.paymentKey()))) {
			if (depositCallback) {
				throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
			}
			return;
		}

		Optional<PgPayment> actual = paymentGateway.findByOrderNo(order.getOrderNo());
		if (actual.isEmpty() || !matchesVirtualAccount(order, payment, actual.get())) {
			log.error("주문 {} 가상계좌 웹훅과 PG 조회 결과가 일치하지 않습니다. 수동 확인이 필요합니다.", order.getOrderNo());
			return;
		}
		PgPayment pg = actual.get();
		if (pg.status() == PgStatus.DONE && payment.getStatus() == PaymentStatus.WAITING_DEPOSIT
				&& order.getStatus() == OrderStatus.WAITING_DEPOSIT) {
			orderTxService.completeVirtualAccountDeposit(order.getId(), payment.getId(), pg);
			return;
		}
		if (pg.status() == PgStatus.DONE && payment.getStatus() == PaymentStatus.DONE
				&& (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.PREPARING
						|| order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED)) {
			return;
		}
		if (pg.status() == PgStatus.WAITING_FOR_DEPOSIT && payment.getStatus() == PaymentStatus.DONE) {
			if (!orderTxService.revertVirtualAccountDeposit(order.getId(), payment.getId(), pg)) {
				log.error("주문 {} 입금 상태가 다시 대기로 바뀌었으나 이미 배송 단계여서 자동 복구하지 못했습니다.",
						order.getOrderNo());
			}
			return;
		}
		if (pg.status() == PgStatus.CANCELED && payment.getStatus() == PaymentStatus.WAITING_DEPOSIT
				&& order.getStatus() == OrderStatus.WAITING_DEPOSIT
				&& payment.getVirtualAccountDueAt() != null
				&& !payment.getVirtualAccountDueAt().isAfter(LocalDateTime.now())) {
			orderTxService.expireVirtualAccount(order.getId(), payment.getId());
			return;
		}
		if (pg.status() == PgStatus.DONE) {
			log.error("주문 {} 입금 완료가 주문 만료 또는 다른 상태와 충돌합니다. 운영자 확인이 필요합니다.", order.getOrderNo());
		}
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

	/** PG 승인은 됐지만 주문 상태가 먼저 바뀐 경우, 자동 보정 또는 환불이 가능하도록 처리한다. */
	private OrderDetailResponse recoverUnappliedApproval(Long memberId, Order order, PgPayment pg) {
		Optional<Payment> processed = paymentRepository.findByPaymentKey(pg.paymentKey());
		if (processed.isPresent()) {
			Payment payment = processed.get();
			if (!payment.getOrderId().equals(order.getId())) {
				throw new BusinessException(ErrorCode.PAYMENT_FAILED);
			}
			if (payment.getStatus() == PaymentStatus.WAITING_DEPOSIT) {
				if (orderTxService.completeVirtualAccountDeposit(order.getId(), payment.getId(), pg)) {
					return orderService.getMyOrder(memberId, order.getOrderNo());
				}
				Optional<Payment> latest = paymentRepository.findByPaymentKey(pg.paymentKey());
				if (latest.isPresent() && latest.get().getStatus() == PaymentStatus.DONE) {
					return orderService.getMyOrder(memberId, order.getOrderNo());
				}
				log.error("주문 {} 가상계좌 입금 반영이 경합으로 지연되어 보정 작업을 기다립니다.", order.getOrderNo());
				throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
			}
			if (payment.getStatus() == PaymentStatus.DONE) {
				return orderService.getMyOrder(memberId, order.getOrderNo());
			}
			if (cancelUnappliedApproval(order, pg)) {
				log.warn("주문 {}의 기존 결제 기록과 PG 승인 결과가 충돌해 PG 승인을 취소했습니다.", order.getOrderNo());
				throw new BusinessException(ErrorCode.PAYMENT_FAILED);
			}
			log.error("주문 {}의 기존 결제 기록과 PG 승인 결과가 충돌합니다. 수동 확인이 필요합니다.", order.getOrderNo());
			throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
		}

		// 승인 응답이 늦는 동안 보정 스케줄러가 주문을 PENDING으로 돌렸다면 즉시 다시 반영한다.
		if (orderTxService.startPayment(order.getId())) {
			if (orderTxService.completePayment(order.getId(), pg)) {
				return orderService.getMyOrder(memberId, order.getOrderNo());
			}
			log.error("주문 {} PG 승인은 완료됐지만 재시도 중 주문 상태가 다시 바뀌었습니다.", order.getOrderNo());
		}

		Order current = orderRepository.findById(order.getId()).orElseThrow();
		if (current.getStatus() == OrderStatus.PAYMENT_PROCESSING
				|| current.getStatus() == OrderStatus.CANCEL_REQUESTED
				|| current.getStatus() == OrderStatus.WAITING_DEPOSIT) {
			log.error("주문 {} PG 승인 결과와 현재 주문 상태({})가 충돌해 보정 작업을 기다립니다.",
					order.getOrderNo(), current.getStatus());
			throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
		}

		if (cancelUnappliedApproval(order, pg)) {
			log.warn("주문 {} 상태가 결제 완료를 허용하지 않아 PG 승인을 취소했습니다.", order.getOrderNo());
			throw new BusinessException(ErrorCode.PAYMENT_FAILED);
		}
		log.error("주문 {} PG 승인 취소 및 DB 반영을 확인하지 못했습니다. 수동 확인이 필요합니다.", order.getOrderNo());
		throw new BusinessException(ErrorCode.PAYMENT_PENDING_CONFIRMATION);
	}

	private boolean cancelUnappliedApproval(Order order, PgPayment pg) {
		try {
			PgPayment canceled = paymentGateway.cancel(pg.paymentKey(), "주문 상태 변경 실패",
					"unapplied-" + order.getOrderNo());
			if (canceled.status() == PgStatus.CANCELED) {
				return true;
			}
		} catch (PgRejectedException | PgUnavailableException e) {
			log.error("주문 {} PG 승인 자동 취소 요청이 실패했습니다.", order.getOrderNo(), e);
		}
		return findAtPg(order.getOrderNo())
				.filter(actual -> pg.paymentKey().equals(actual.paymentKey()) && actual.status() == PgStatus.CANCELED)
				.isPresent();
	}

	private void cancelMismatchedPayment(Order order, PgPayment pg) {
		try {
			paymentGateway.cancel(pg.paymentKey(), "결제 금액 불일치", "mismatch-" + order.getOrderNo());
		} catch (PgRejectedException | PgUnavailableException e) {
			log.error("금액 불일치 결제 {}의 자동 환불 실패. 수동 환불이 필요합니다.", pg.paymentKey(), e);
		}
	}

	private boolean matchesVirtualAccount(Order order, Payment payment, PgPayment pg) {
		return "가상계좌".equals(pg.method())
				&& order.getOrderNo().equals(pg.orderNo())
				&& payment.getPaymentKey().equals(pg.paymentKey())
				&& pg.totalAmount() == order.getTotalAmount()
				&& payment.matchesWebhookSecret(pg.secret());
	}
}
