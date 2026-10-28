package com.example.shopping.domain.payment.service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.order.service.OrderTxService;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.entity.PaymentStatus;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.example.shopping.global.monitoring.ManualCheckAlerts;

import lombok.RequiredArgsConstructor;

/**
 * 가상계좌 결제를 PG 조회 결과에 맞춘다. 입금 웹훅(PaymentService)과 보정 작업(OrderMaintenanceScheduler)이
 * 같은 기준으로 판단하도록 판단을 여기 한 곳에 둔다.
 *
 * <pre>
 * PG 상태                       | 결제     | 주문                                | 처리
 * DONE                          | 입금 대기 | 입금 대기                            | 입금 완료 반영
 * DONE                          | 완료     | 결제 완료·상품 준비·배송 중·배송 완료  | 이미 반영됨
 * DONE                          | 그 밖    | 그 밖                               | 운영자 확인(돈은 들어왔는데 주문이 다른 상태)
 * WAITING_FOR_DEPOSIT           | 완료     | 결제 완료·상품 준비                   | 입금 대기로 되돌림(배송 단계면 운영자 확인)
 * WAITING_FOR_DEPOSIT, CANCELED | 입금 대기 | 입금 대기                            | 입금 기한이 지났으면 만료
 * </pre>
 * PG 결과가 저장된 결제와 맞지 않으면(PgPaymentMatcher) 아무것도 바꾸지 않고 운영자 확인을 남긴다.
 */
@Component
@RequiredArgsConstructor
public class VirtualAccountReconciler {

	private static final Set<OrderStatus> DEPOSIT_APPLIED = EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING,
			OrderStatus.SHIPPED, OrderStatus.DELIVERED);

	private final OrderTxService orderTxService;
	private final OrderRepository orderRepository;
	private final ManualCheckAlerts alerts;

	public void reconcile(Order order, Payment payment, PgPayment pg) {
		if (!PgPaymentMatcher.isSameVirtualAccount(order, payment, pg)) {
			alerts.raise("virtual_account_mismatch", "주문 {} 가상계좌 PG 조회 결과가 저장된 결제와 맞지 않습니다.",
					order.getOrderNo());
			return;
		}
		boolean waiting = payment.getStatus() == PaymentStatus.WAITING_DEPOSIT
				&& order.getStatus() == OrderStatus.WAITING_DEPOSIT;
		if (pg.status() == PgStatus.DONE) {
			applyDeposit(order, payment, pg, waiting);
		} else if (pg.status() == PgStatus.WAITING_FOR_DEPOSIT && payment.getStatus() == PaymentStatus.DONE) {
			if (!orderTxService.revertVirtualAccountDeposit(order.getId(), payment.getId(), pg)) {
				alerts.raise("deposit_revert_blocked", "주문 {} 입금이 다시 대기로 바뀌었으나 이미 배송 단계여서 자동으로 되돌리지 못했습니다.",
						order.getOrderNo());
			}
		} else if ((pg.status() == PgStatus.WAITING_FOR_DEPOSIT || pg.status() == PgStatus.CANCELED) && waiting
				&& !payment.getVirtualAccountDueAt().isAfter(LocalDateTime.now())) {
			orderTxService.expireVirtualAccount(order.getId(), payment.getId());
		}
	}

	private void applyDeposit(Order order, Payment payment, PgPayment pg, boolean waiting) {
		if (waiting && orderTxService.completeVirtualAccountDeposit(order.getId(), payment.getId(), pg)) {
			return;
		}
		// 반영하지 못했어도 다른 경로(웹훅·보정 작업)가 먼저 반영했으면 문제없다.
		OrderStatus current = waiting
				? orderRepository.findById(order.getId()).map(Order::getStatus).orElse(order.getStatus())
				: order.getStatus();
		boolean applied = DEPOSIT_APPLIED.contains(current)
				&& (waiting || payment.getStatus() == PaymentStatus.DONE);
		if (!applied) {
			alerts.raise("deposit_conflict", "주문 {} 입금은 완료됐지만 주문이 {} 상태라 반영하지 못했습니다.", order.getOrderNo(),
					current);
		}
	}
}
