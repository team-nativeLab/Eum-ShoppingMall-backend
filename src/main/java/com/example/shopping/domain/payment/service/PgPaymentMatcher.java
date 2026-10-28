package com.example.shopping.domain.payment.service;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.gateway.PgPayment;

/** PG 조회 결과가 저장된 주문·결제의 것인지 확인한다. 웹훅·결제 보정·가상계좌 보정·취소 보정이 같은 기준을 쓴다. */
public final class PgPaymentMatcher {

	private PgPaymentMatcher() {
	}

	/** 주문번호·결제 키·결제 금액이 모두 같은지 */
	public static boolean isSamePayment(Order order, Payment payment, PgPayment pg) {
		return order.getOrderNo().equals(pg.orderNo())
				&& payment.getPaymentKey().equals(pg.paymentKey())
				&& pg.totalAmount() == order.getTotalAmount();
	}

	/** 같은 결제이고, 저장된 결제와 PG 결과가 모두 가상계좌이며, 발급 때 받은 계좌 정보와 웹훅 비밀값이 그대로인지 */
	public static boolean isSameVirtualAccount(Order order, Payment payment, PgPayment pg) {
		return isSamePayment(order, payment, pg)
				&& payment.isVirtualAccount()
				&& Payment.VIRTUAL_ACCOUNT_METHOD.equals(pg.method())
				&& payment.getVirtualAccountNumber() != null
				&& payment.getVirtualAccountDueAt() != null
				&& payment.matchesWebhookSecret(pg.secret());
	}
}
