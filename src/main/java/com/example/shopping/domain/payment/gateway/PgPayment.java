package com.example.shopping.domain.payment.gateway;

import java.time.LocalDateTime;

/**
 * PG가 알려준 결제 상태.
 *
 * 응답 원문은 담지 않는다. 결제 수단 정보가 들어 있어 저장하지 않기 때문이다(#144).
 *
 * @param totalAmount   PG가 실제로 승인한 금액. 주문 금액과 반드시 다시 대조한다.
 * @param balanceAmount 취소하고 남은 금액
 */
public record PgPayment(String paymentKey, String orderNo, PgStatus status, long totalAmount, long balanceAmount,
		String method, LocalDateTime approvedAt, PgVirtualAccount virtualAccount, String secret) {

	public PgPayment(String paymentKey, String orderNo, PgStatus status, long totalAmount, long balanceAmount,
			String method, LocalDateTime approvedAt) {
		this(paymentKey, orderNo, status, totalAmount, balanceAmount, method, approvedAt, null, null);
	}

	public enum PgStatus {
		/** 결제 완료 */
		DONE,
		/** 가상계좌 발급 후 입금 대기 */
		WAITING_FOR_DEPOSIT,
		/** 전액 취소됨 */
		CANCELED,
		/** 그 밖의 상태(진행 중, 만료, 중단 등) */
		OTHER
	}

	/** 돈이 결제됐거나 결제될 계좌가 발급된 상태인지 */
	public boolean isApproved() {
		return status == PgStatus.DONE || status == PgStatus.WAITING_FOR_DEPOSIT;
	}
}
