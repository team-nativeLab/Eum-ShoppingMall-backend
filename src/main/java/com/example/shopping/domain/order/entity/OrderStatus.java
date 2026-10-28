package com.example.shopping.domain.order.entity;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
	PENDING, PAYMENT_PROCESSING, WAITING_DEPOSIT, PAID,
	PREPARING, CANCEL_REQUESTED, SHIPPED, DELIVERED,
	CANCELED, EXPIRED;

	/**
	 * 주문 상태가 바뀔 수 있는 모든 경우. 상태를 바꾸는 곳(관리자 상태 변경, OrderTxService의 상태 변경 쿼리)은
	 * 모두 이 표로 확인하므로, 새 흐름을 만들면 여기에 먼저 추가해야 한다.
	 */
	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
			// 결제 시작, 결제 기한 만료, 관리자 취소
			PENDING, Set.of(PAYMENT_PROCESSING, EXPIRED, CANCEL_REQUESTED),
			// 승인, 가상계좌 발급, 승인 실패·보정으로 결제 전 되돌림
			PAYMENT_PROCESSING, Set.of(PAID, WAITING_DEPOSIT, PENDING),
			// 입금 확인, 입금 기한 만료, 관리자 취소
			WAITING_DEPOSIT, Set.of(PAID, EXPIRED, CANCEL_REQUESTED),
			// 상품 준비(관리자), 취소 요청, PG의 입금 취소 통보
			PAID, Set.of(PREPARING, CANCEL_REQUESTED, WAITING_DEPOSIT),
			// 배송 시작(관리자), 취소 요청, PG의 입금 취소 통보
			PREPARING, Set.of(SHIPPED, CANCEL_REQUESTED, WAITING_DEPOSIT),
			// 취소 완료, 미결제 주문 취소(만료), PG가 취소를 거절하면 취소 전 상태로
			CANCEL_REQUESTED, Set.of(CANCELED, EXPIRED, PENDING, WAITING_DEPOSIT, PAID, PREPARING),
			// 배송 완료(관리자)
			SHIPPED, Set.of(DELIVERED));

	/** 결제·배송·취소가 끝나지 않은 주문. 이런 주문이 있으면 회원 탈퇴를 막는다. */
	public static final Set<OrderStatus> IN_PROGRESS = EnumSet.of(PENDING, PAYMENT_PROCESSING, WAITING_DEPOSIT,
			PAID, PREPARING, SHIPPED, CANCEL_REQUESTED);

	public boolean canTransitTo(OrderStatus next) {
		return ALLOWED.getOrDefault(this, Set.of()).contains(next);
	}

	/**
	 * 표에 없는 전이를 하려고 하면 프로그래밍 오류로 보고 바로 실패시킨다.
	 * 상태 변경 쿼리는 출발·도착 상태를 이 확인을 거친 값으로만 받으므로, 쿼리와 표가 어긋나면 테스트에서 드러난다.
	 */
	public static void requireTransitions(Collection<OrderStatus> from, OrderStatus to) {
		for (OrderStatus source : from) {
			if (!source.canTransitTo(to)) {
				throw new IllegalStateException("Order status transition is not allowed: " + source + " -> " + to);
			}
		}
	}
}
