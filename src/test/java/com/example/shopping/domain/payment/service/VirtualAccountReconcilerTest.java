package com.example.shopping.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.order.service.OrderTxService;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.entity.PaymentStatus;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.example.shopping.global.monitoring.ManualCheckAlerts;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** 웹훅과 보정 작업이 함께 쓰는 가상계좌 판단표의 각 줄 (#108) */
class VirtualAccountReconcilerTest {

	private static final long ORDER_ID = 1L;
	private static final long PAYMENT_ID = 10L;
	private static final LocalDateTime PAST_DUE = LocalDateTime.now().minusHours(1);
	private static final LocalDateTime FUTURE_DUE = LocalDateTime.now().plusDays(1);

	private final OrderTxService orderTxService = mock(OrderTxService.class);
	private final OrderRepository orderRepository = mock(OrderRepository.class);
	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final VirtualAccountReconciler reconciler = new VirtualAccountReconciler(orderTxService, orderRepository,
			new ManualCheckAlerts(meters));

	@Test
	void 입금_대기_주문에_입금되면_결제_완료로_반영한다() {
		Order order = order(OrderStatus.WAITING_DEPOSIT);
		Payment payment = payment(PaymentStatus.WAITING_DEPOSIT, FUTURE_DUE);
		PgPayment pg = pg(PgStatus.DONE, 35000);
		when(orderTxService.completeVirtualAccountDeposit(ORDER_ID, PAYMENT_ID, pg)).thenReturn(true);

		reconciler.reconcile(order, payment, pg);

		verify(orderTxService).completeVirtualAccountDeposit(ORDER_ID, PAYMENT_ID, pg);
		assertThat(alerts("deposit_conflict")).isZero();
	}

	@Test
	void 이미_반영된_입금은_그대로_두고_다른_경로가_먼저_반영한_경우도_경고하지_않는다() {
		reconciler.reconcile(order(OrderStatus.PAID), payment(PaymentStatus.DONE, FUTURE_DUE), pg(PgStatus.DONE, 35000));
		verifyNoInteractions(orderTxService);

		// 반영하려는 사이 웹훅과 보정 작업 중 다른 쪽이 먼저 반영했다.
		Order waiting = order(OrderStatus.WAITING_DEPOSIT);
		Order paid = order(OrderStatus.PAID);
		when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(paid));
		reconciler.reconcile(waiting, payment(PaymentStatus.WAITING_DEPOSIT, FUTURE_DUE), pg(PgStatus.DONE, 35000));

		assertThat(alerts("deposit_conflict")).isZero();
	}

	@Test
	void 입금은_완료됐는데_주문이_만료됐으면_운영자_확인을_남긴다() {
		reconciler.reconcile(order(OrderStatus.EXPIRED), payment(PaymentStatus.WAITING_DEPOSIT, PAST_DUE),
				pg(PgStatus.DONE, 35000));

		verify(orderTxService, never()).completeVirtualAccountDeposit(anyLong(), anyLong(), any());
		assertThat(alerts("deposit_conflict")).isEqualTo(1);
	}

	@Test
	void 입금이_다시_대기로_바뀌면_되돌리고_배송_단계라_못_되돌리면_운영자_확인을_남긴다() {
		PgPayment pg = pg(PgStatus.WAITING_FOR_DEPOSIT, 35000);
		when(orderTxService.revertVirtualAccountDeposit(ORDER_ID, PAYMENT_ID, pg)).thenReturn(false);

		reconciler.reconcile(order(OrderStatus.SHIPPED), payment(PaymentStatus.DONE, FUTURE_DUE), pg);

		verify(orderTxService).revertVirtualAccountDeposit(ORDER_ID, PAYMENT_ID, pg);
		assertThat(alerts("deposit_revert_blocked")).isEqualTo(1);
	}

	@Test
	void 입금_기한이_지났으면_PG가_입금_대기든_취소든_만료하고_기한_전이면_그대로_둔다() {
		for (PgStatus status : new PgStatus[] { PgStatus.WAITING_FOR_DEPOSIT, PgStatus.CANCELED }) {
			reconciler.reconcile(order(OrderStatus.WAITING_DEPOSIT), payment(PaymentStatus.WAITING_DEPOSIT, PAST_DUE),
					pg(status, 35000));
		}
		verify(orderTxService, times(2)).expireVirtualAccount(ORDER_ID, PAYMENT_ID);

		reconciler.reconcile(order(OrderStatus.WAITING_DEPOSIT), payment(PaymentStatus.WAITING_DEPOSIT, FUTURE_DUE),
				pg(PgStatus.CANCELED, 35000));
		verify(orderTxService, times(2)).expireVirtualAccount(ORDER_ID, PAYMENT_ID);
	}

	@Test
	void PG_결과가_저장된_결제와_다르면_아무것도_바꾸지_않고_운영자_확인을_남긴다() {
		reconciler.reconcile(order(OrderStatus.WAITING_DEPOSIT), payment(PaymentStatus.WAITING_DEPOSIT, PAST_DUE),
				pg(PgStatus.DONE, 1));

		verifyNoInteractions(orderTxService);
		assertThat(alerts("virtual_account_mismatch")).isEqualTo(1);
	}

	private double alerts(String reason) {
		Counter counter = meters.find("manual.check.required").tag("reason", reason).counter();
		return counter == null ? 0 : counter.count();
	}

	private static Order order(OrderStatus status) {
		Order order = mock(Order.class);
		when(order.getId()).thenReturn(ORDER_ID);
		when(order.getOrderNo()).thenReturn("ORD-1");
		when(order.getTotalAmount()).thenReturn(35000L);
		when(order.getStatus()).thenReturn(status);
		return order;
	}

	private static Payment payment(PaymentStatus status, LocalDateTime dueAt) {
		Payment payment = mock(Payment.class);
		when(payment.getId()).thenReturn(PAYMENT_ID);
		when(payment.getPaymentKey()).thenReturn("pay-1");
		when(payment.getStatus()).thenReturn(status);
		when(payment.isVirtualAccount()).thenReturn(true);
		when(payment.getVirtualAccountNumber()).thenReturn("110-123-456789");
		when(payment.getVirtualAccountDueAt()).thenReturn(dueAt);
		when(payment.matchesWebhookSecret("secret")).thenReturn(true);
		return payment;
	}

	private static PgPayment pg(PgStatus status, long amount) {
		return new PgPayment("pay-1", "ORD-1", status, amount, amount, Payment.VIRTUAL_ACCOUNT_METHOD, null, "{}", null,
				"secret");
	}
}
