package com.example.shopping.domain.payment.gateway;

import java.util.Optional;

/**
 * 결제대행사(PG) 연동. PG사를 바꿀 때는 이 인터페이스의 구현만 교체한다.
 * 모든 메서드는 PG가 명확히 거절하면 PgRejectedException, 결과를 알 수 없으면(타임아웃·PG 장애) PgUnavailableException을 던진다.
 */
public interface PaymentGateway {

	/** 결제창 인증을 마친 결제를 최종 승인한다. */
	PgPayment confirm(String paymentKey, String orderNo, long amount);

	/** 주문번호로 PG에 기록된 실제 결제 상태를 조회한다. 결제 기록이 없으면 빈 값 */
	Optional<PgPayment> findByOrderNo(String orderNo);

	/** 결제 전액을 취소(환불)한다. 같은 idempotencyKey로 다시 보내도 한 번만 취소된다. */
	PgPayment cancel(String paymentKey, String reason, String idempotencyKey);
}
