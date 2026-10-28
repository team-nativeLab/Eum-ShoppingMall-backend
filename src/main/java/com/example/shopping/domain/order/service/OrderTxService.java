package com.example.shopping.domain.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.shopping.domain.cart.repository.CartItemRepository;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.entity.PaymentStatus;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgPayment.PgStatus;
import com.example.shopping.domain.payment.repository.PaymentRepository;
import com.example.shopping.domain.product.repository.ProductRepository;
import com.example.shopping.domain.product.repository.ProductSkuRepository;
import com.example.shopping.domain.product.service.ProductReadCache;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 주문 상태를 바꾸는 짧은 트랜잭션들. PG 호출처럼 오래 걸리는 외부 통신은 이 밖에서 하고,
 * 그 앞뒤의 DB 반영만 여기서 한다 (외부 응답을 기다리는 동안 DB 커넥션을 잡고 있지 않기 위해).
 */
@Service
@RequiredArgsConstructor
public class OrderTxService {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final ProductSkuRepository productSkuRepository;
	private final ProductRepository productRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductReadCache productReadCache;

	/** 결제 승인을 시작한다. 이미 진행 중이거나 끝났거나 기한이 지났으면 false */
	@Transactional
	public boolean startPayment(Long orderId) {
		return transition(Set.of(OrderStatus.PENDING), OrderStatus.PAYMENT_PROCESSING,
				(from, to) -> orderRepository.startPayment(orderId, from, to, LocalDateTime.now())) == 1;
	}

	/** 승인이 확정적으로 실패했을 때 다시 결제를 시도할 수 있게 되돌린다. */
	@Transactional
	public void revertPayment(Long orderId) {
		transition(Set.of(OrderStatus.PAYMENT_PROCESSING), OrderStatus.PENDING,
				(from, to) -> orderRepository.changeStatus(orderId, from, to, LocalDateTime.now()));
	}

	/**
	 * PG 승인 결과를 반영한다. 결제가 끝난 품목은 장바구니에서 뺀다.
	 *
	 * @return 결제 완료나 입금 대기를 주문에 반영했으면 true. 그사이 주문 상태가 바뀌어(결제 보정 작업이 되돌림 등)
	 *         반영하지 못했거나, PG 상태가 결제 완료·입금 대기가 아니어서 결제 전으로 되돌렸으면 false
	 */
	@Transactional
	public boolean completePayment(Long orderId, PgPayment pg) {
		LocalDateTime now = LocalDateTime.now();
		if (pg.status() == PgStatus.DONE) {
			if (transition(Set.of(OrderStatus.PAYMENT_PROCESSING), OrderStatus.PAID,
					(from, to) -> orderRepository.markPaid(orderId, from, to, now)) != 1) {
				return false;
			}
			paymentRepository.save(Payment.of(orderId, pg, PaymentStatus.DONE));
			addSalesCount(orderId, 1);
			removeOrderedItemsFromCart(orderId);
			return true;
		}
		if (pg.status() == PgStatus.WAITING_FOR_DEPOSIT) {
			if (!hasVirtualAccountDetails(pg)) {
				return false;
			}
			if (transition(Set.of(OrderStatus.PAYMENT_PROCESSING), OrderStatus.WAITING_DEPOSIT,
					(from, to) -> orderRepository.markWaitingDeposit(orderId, from, to,
							pg.virtualAccount().dueDate(), now)) == 1) {
				paymentRepository.save(Payment.of(orderId, pg, PaymentStatus.WAITING_DEPOSIT));
				removeOrderedItemsFromCart(orderId);
				return true;
			}
			return false;
		}
		transition(Set.of(OrderStatus.PAYMENT_PROCESSING), OrderStatus.PENDING,
				(from, to) -> orderRepository.changeStatus(orderId, from, to, now));
		return false;
	}

	/** PG 조회에서 입금 완료가 확인된 가상계좌 주문을 결제로 확정한다. 중복 웹훅은 상태 조건으로 막는다. */
	@Transactional
	public boolean completeVirtualAccountDeposit(Long orderId, Long paymentId, PgPayment pg) {
		if (transition(Set.of(OrderStatus.WAITING_DEPOSIT), OrderStatus.PAID,
				(from, to) -> orderRepository.markPaid(orderId, from, to, LocalDateTime.now())) != 1) {
			return false;
		}
		Payment payment = paymentRepository.findById(paymentId).orElseThrow();
		payment.recordDeposit(pg);
		addSalesCount(orderId, 1);
		return true;
	}

	/** PG 확인 결과 입금 기한이 지나 입금되지 않은 가상계좌 주문의 재고를 해제한다. */
	@Transactional
	public boolean expireVirtualAccount(Long orderId, Long paymentId) {
		LocalDateTime now = LocalDateTime.now();
		if (transition(Set.of(OrderStatus.WAITING_DEPOSIT), OrderStatus.EXPIRED,
				(from, to) -> orderRepository.expire(orderId, from, to, now)) != 1) {
			return false;
		}
		Payment payment = paymentRepository.findById(paymentId).orElseThrow();
		payment.expireVirtualAccount();
		restoreStock(orderId);
		return true;
	}

	/** PG가 DONE 결제를 다시 입금 대기로 바꾼 경우 미배송 주문을 보류하고 판매 수량을 되돌린다. */
	@Transactional
	public boolean revertVirtualAccountDeposit(Long orderId, Long paymentId, PgPayment pg) {
		if (transition(Set.of(OrderStatus.PAID, OrderStatus.PREPARING), OrderStatus.WAITING_DEPOSIT,
				(from, to) -> orderRepository.revertDepositCompletion(orderId, from, to, LocalDateTime.now())) != 1) {
			return false;
		}
		Payment payment = paymentRepository.findById(paymentId).orElseThrow();
		payment.revertDeposit(pg);
		addSalesCount(orderId, -1);
		return true;
	}

	/** 결제 기한이 지난 주문을 만료시키고 잡아 둔 재고를 되돌린다. 실제로 만료시켰으면 true */
	@Transactional
	public boolean expire(Long orderId) {
		if (transition(Set.of(OrderStatus.PENDING), OrderStatus.EXPIRED,
				(from, to) -> orderRepository.expire(orderId, from, to, LocalDateTime.now())) == 0) {
			return false;
		}
		restoreStock(orderId);
		return true;
	}

	/**
	 * 회원이 결제하지 않고 남겨 둔 주문을 만료시키고 재고를 되돌린다. 새 주문을 만들기 직전에 부른다.
	 * 결제 없이 주문만 반복해 만들어 재고를 계속 묶어 두는 것을 막기 위함이다 (회원당 결제 대기 주문은 하나만 남는다).
	 */
	@Transactional
	public void expirePendingOrdersOf(Long memberId) {
		LocalDateTime now = LocalDateTime.now();
		for (Long orderId : orderRepository.findPendingIdsByMemberId(memberId)) {
			if (transition(Set.of(OrderStatus.PENDING), OrderStatus.EXPIRED,
					(from, to) -> orderRepository.changeStatus(orderId, from, to, now)) == 1) {
				restoreStock(orderId);
			}
		}
	}

	/** 취소를 시작한다. 그사이 상태가 바뀌었으면(배송 시작 등) false */
	@Transactional
	public boolean claimCancel(Long orderId, OrderStatus from, String idempotencyKey, String reason, Long actorId) {
		return transition(Set.of(from), OrderStatus.CANCEL_REQUESTED,
				(sources, to) -> orderRepository.claimCancel(orderId, from, to, idempotencyKey, reason, actorId,
						LocalDateTime.now())) == 1;
	}

	/** 관리자 요청으로 미결제 주문을 만료한다. 가상계좌 결제는 PG 취소 확인 후 호출한다. */
	@Transactional
	public void completeUnpaidAdminCancellation(Long orderId, Long paymentId) {
		if (transition(Set.of(OrderStatus.CANCEL_REQUESTED), OrderStatus.EXPIRED,
				(from, to) -> orderRepository.finishUnpaidCancellation(orderId, from, to, LocalDateTime.now())) != 1) {
			return;
		}
		if (paymentId != null) {
			Payment payment = paymentRepository.findById(paymentId).orElseThrow();
			if (payment.getStatus() == PaymentStatus.WAITING_DEPOSIT) {
				payment.expireVirtualAccount();
			}
		}
		orderRepository.cancelItems(orderId);
		restoreStock(orderId);
	}

	/** PG 취소가 확실히 거절됐을 때 주문을 취소 전 상태로 되돌린다. */
	@Transactional
	public void revertCancel(Long orderId, OrderStatus to) {
		transition(Set.of(OrderStatus.CANCEL_REQUESTED), to, (from, target) -> orderRepository.revertCancel(orderId,
				OrderStatus.CANCEL_REQUESTED, target, LocalDateTime.now()));
	}

	/** PG 취소가 확인된 뒤 결제·주문·품목 상태와 재고를 반영한다. 두 번 불려도 한 번만 반영된다. */
	@Transactional
	public void completeCancel(Long orderId, Long paymentId) {
		transition(Set.of(OrderStatus.CANCEL_REQUESTED), OrderStatus.CANCELED,
				(from, to) -> orderRepository.finishCancel(orderId, from, to, LocalDateTime.now()));
		if (orderRepository.isCanceled(orderId)) {
			orderRepository.clearCancelRecoveryData(orderId);
		}
		if (orderRepository.isCanceled(orderId) && paymentRepository.markCanceled(paymentId) == 1) {
			orderRepository.cancelItems(orderId);
			restoreStock(orderId);
			addSalesCount(orderId, -1);
		}
	}

	/** 주기 보정 작업을 조건부로 점유한다. 동시 실행 중 한 서버만 PG를 호출한다. */
	@Transactional
	public boolean claimCancellationRecovery(Long orderId, LocalDateTime now) {
		return orderRepository.claimCancellationRecovery(orderId, now.minusMinutes(5), now) == 1;
	}

	private void restoreStock(Long orderId) {
		List<long[]> quantities = orderedQuantities(orderId);
		quantities.forEach(q -> productSkuRepository.increaseStock(q[0], (int) q[1]));
		if (!quantities.isEmpty()) {
			productReadCache.invalidateAfterCommit();
		}
	}

	/** 주문한 수량만큼 상품의 누적 판매 수량(인기순 정렬 기준)을 더하거나(sign = 1, 결제 완료) 뺀다(sign = -1, 취소). */
	private void addSalesCount(Long orderId, int sign) {
		List<long[]> quantities = orderedQuantities(orderId);
		quantities.forEach(q -> productRepository.addSalesCount(q[0], sign * q[1]));
		if (!quantities.isEmpty()) {
			productReadCache.invalidateAfterCommit();
		}
	}

	/** 주문 품목의 {옵션 ID, 수량} 목록. 재고·판매 수량 쿼리가 영속성 컨텍스트를 비우므로 값을 먼저 꺼내 둔다. */
	private List<long[]> orderedQuantities(Long orderId) {
		return findOrder(orderId).getItems().stream()
				.map(item -> new long[] { item.getSkuId(), item.getQuantity() })
				.toList();
	}

	private void removeOrderedItemsFromCart(Long orderId) {
		Order order = findOrder(orderId);
		List<Long> skuIds = order.getItems().stream().map(item -> item.getSkuId()).toList();
		cartItemRepository.deleteByMemberIdAndSkuIds(order.getMemberId(), skuIds);
	}

	private boolean hasVirtualAccountDetails(PgPayment pg) {
		return pg.virtualAccount() != null
				&& pg.virtualAccount().bankCode() != null
				&& pg.virtualAccount().accountNumber() != null
				&& pg.virtualAccount().customerName() != null
				&& pg.virtualAccount().dueDate() != null
				&& pg.secret() != null;
	}

	private Order findOrder(Long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	/**
	 * 상태 전이표(OrderStatus)에 있는 전이인지 확인한 뒤 상태 변경 쿼리를 실행한다.
	 * 쿼리는 여기서 확인한 출발·도착 상태만 받으므로, 쿼리를 고쳐도 표와 어긋날 수 없다.
	 */
	private static int transition(Set<OrderStatus> from, OrderStatus to,
			BiFunction<Set<OrderStatus>, OrderStatus, Integer> query) {
		OrderStatus.requireTransitions(from, to);
		return query.apply(from, to);
	}
}
