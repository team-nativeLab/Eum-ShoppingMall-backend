package com.example.shopping.domain.payment.gateway;

import java.time.LocalDateTime;

/**
 * PG가 알려준 결제 상태.
 *
 * @param totalAmount   PG가 실제로 승인한 금액. 주문 금액과 반드시 다시 대조한다.
 * @param balanceAmount 취소하고 남은 금액
 * @param raw           PG 응답 원문
 */
public record PgPayment(String paymentKey, String orderNo, PgStatus status, long totalAmount, long balanceAmount,
		String method, LocalDateTime approvedAt, String raw) {

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
