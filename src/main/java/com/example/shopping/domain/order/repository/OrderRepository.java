package com.example.shopping.domain.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;

/**
 * 상태를 바꾸는 쿼리는 모두 "현재 상태가 기대한 값일 때만" 바꾸고 바뀐 행 수를 돌려준다.
 * 같은 주문에 요청이 동시에 들어와도 한 요청만 1을 받으므로, 승인·취소·만료·재고 복구가 두 번 일어나지 않는다.
 * 관리자 화면의 수정과도 충돌을 감지할 수 있도록 version을 함께 올린다.
 * 출발·도착 상태는 OrderTxService가 상태 전이표(OrderStatus)로 확인한 값을 인자로 받는다.
 * 일괄 수정은 영속성 컨텍스트를 거치지 않으므로, 이미 읽어 둔 엔티티가 옛 상태로 남지 않게 실행 뒤 비운다(clearAutomatically).
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByOrderNo(String orderNo);

	Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

	Page<Order> findByMemberId(Long memberId, Pageable pageable);

	/** 세 조건 모두 선택이다. 주문번호는 일부만 입력해도 찾는다. */
	@Query("""
			select o from Order o
			where (:status is null or o.status = :status)
			  and (:memberId is null or o.memberId = :memberId)
			  and (:orderNo is null or o.orderNo like concat('%', :orderNo, '%'))
			""")
	Page<Order> search(@Param("status") OrderStatus status, @Param("memberId") Long memberId,
			@Param("orderNo") String orderNo, Pageable pageable);

	/** 기간 내에 결제된 주문 수. 전체 취소된 주문은 뺀다. */
	@Query("""
			select count(o) from Order o
			where o.paidAt >= :from
			  and o.status <> com.example.shopping.domain.order.entity.OrderStatus.CANCELED
			""")
	long countPaidSince(@Param("from") LocalDateTime from);

	/** 기간 내에 결제된 주문의 결제 금액 합계. 전체 취소된 주문은 뺀다. */
	@Query("""
			select coalesce(sum(o.totalAmount), 0L) from Order o
			where o.paidAt >= :from
			  and o.status <> com.example.shopping.domain.order.entity.OrderStatus.CANCELED
			""")
	long sumPaidAmountSince(@Param("from") LocalDateTime from);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o set o.status = :to, o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status in :from
			""")
	int changeStatus(@Param("id") Long id, @Param("from") Collection<OrderStatus> from, @Param("to") OrderStatus to,
			@Param("now") LocalDateTime now);

	/** 취소 전 상태를 함께 보존하고 배송을 막는다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o
			set o.status = :to,
			    o.cancelPreviousStatus = :from, o.cancelRequestedAt = :now,
			    o.cancelIdempotencyKey = :idempotencyKey, o.cancelReason = :reason,
			    o.cancelledByMemberId = :actorId,
			    o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status = :from
			""")
	int claimCancel(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to,
			@Param("idempotencyKey") String idempotencyKey, @Param("reason") String reason,
			@Param("actorId") Long actorId, @Param("now") LocalDateTime now);

	/** PG가 취소를 확실히 거절했을 때만 취소 전 상태로 되돌린다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o
			set o.status = :to, o.cancelPreviousStatus = null,
			    o.cancelRequestedAt = null, o.cancelIdempotencyKey = null,
			    o.cancelReason = null, o.cancelledByMemberId = null,
			    o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status = :from and o.cancelPreviousStatus = :to
			""")
	int revertCancel(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to,
			@Param("now") LocalDateTime now);

	/** 취소 확정 후 처리 중 상태를 최종 취소로 바꾼다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o
			set o.status = :to,
			    o.cancelPreviousStatus = null, o.cancelRequestedAt = null, o.cancelIdempotencyKey = null,
			    o.cancelledAt = :now,
			    o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status in :from
			""")
	int finishCancel(@Param("id") Long id, @Param("from") Collection<OrderStatus> from, @Param("to") OrderStatus to,
			@Param("now") LocalDateTime now);

	/** 관리자 요청으로 결제 전 주문을 만료한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o
			set o.status = :to,
			    o.cancelRequestedAt = null, o.cancelPreviousStatus = null, o.cancelIdempotencyKey = null,
			    o.cancelledAt = :now, o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status in :from
			  and o.cancelPreviousStatus in (com.example.shopping.domain.order.entity.OrderStatus.PENDING, com.example.shopping.domain.order.entity.OrderStatus.WAITING_DEPOSIT)
			""")
	int finishUnpaidCancellation(@Param("id") Long id, @Param("from") Collection<OrderStatus> from,
			@Param("to") OrderStatus to, @Param("now") LocalDateTime now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o
			set o.cancelRequestedAt = null, o.cancelPreviousStatus = null, o.cancelIdempotencyKey = null
			where o.id = :id and o.status = com.example.shopping.domain.order.entity.OrderStatus.CANCELED
			""")
	int clearCancelRecoveryData(@Param("id") Long id);

	@Query("""
			select count(o) > 0 from Order o
			where o.id = :id and o.status = com.example.shopping.domain.order.entity.OrderStatus.CANCELED
			""")
	boolean isCanceled(@Param("id") Long id);

	/** 서버가 여러 대여도 오래된 취소 보정 작업은 한 서버만 실행한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o
			set o.cancelRequestedAt = coalesce(o.cancelRequestedAt, o.updatedAt),
			    o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.updatedAt < :before and (
			    o.status = com.example.shopping.domain.order.entity.OrderStatus.CANCEL_REQUESTED
			    or (o.status = com.example.shopping.domain.order.entity.OrderStatus.CANCELED and exists (
			        select p.id from Payment p where p.orderId = o.id
			          and p.status in (com.example.shopping.domain.payment.entity.PaymentStatus.DONE,
			                          com.example.shopping.domain.payment.entity.PaymentStatus.WAITING_DEPOSIT)
			    ))
			)
			""")
	int claimCancellationRecovery(@Param("id") Long id, @Param("before") LocalDateTime before,
			@Param("now") LocalDateTime now);

	/** 결제 승인을 시작한다. 결제 기한이 지난 주문은 시작할 수 없다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o set o.status = :to, o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status in :from and o.expiresAt > :now
			""")
	int startPayment(@Param("id") Long id, @Param("from") Collection<OrderStatus> from, @Param("to") OrderStatus to,
			@Param("now") LocalDateTime now);

	/** 카드 승인이나 가상계좌 입금이 확인된 주문을 결제 완료로 바꾸고 결제 시각을 남긴다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o set o.status = :to, o.paidAt = :now, o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status in :from
			""")
	int markPaid(@Param("id") Long id, @Param("from") Collection<OrderStatus> from, @Param("to") OrderStatus to,
			@Param("now") LocalDateTime now);

	/** 가상계좌 발급 결과와 실제 입금 기한을 함께 저장한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o set o.status = :to, o.expiresAt = :dueDate, o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status in :from
			""")
	int markWaitingDeposit(@Param("id") Long id, @Param("from") Collection<OrderStatus> from,
			@Param("to") OrderStatus to, @Param("dueDate") LocalDateTime dueDate, @Param("now") LocalDateTime now);

	/** 드문 PG 입금 취소/복귀 통보가 오면 아직 배송 전인 주문을 다시 입금 대기로 잠근다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o set o.status = :to, o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status in :from
			""")
	int revertDepositCompletion(@Param("id") Long id, @Param("from") Collection<OrderStatus> from,
			@Param("to") OrderStatus to, @Param("now") LocalDateTime now);

	/** 결제 기한(가상계좌는 입금 기한)이 지난 주문을 만료시킨다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Order o set o.status = :to, o.updatedAt = :now, o.version = o.version + 1
			where o.id = :id and o.status in :from and o.expiresAt <= :now
			""")
	int expire(@Param("id") Long id, @Param("from") Collection<OrderStatus> from, @Param("to") OrderStatus to,
			@Param("now") LocalDateTime now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update OrderItem i set i.status = com.example.shopping.domain.order.entity.OrderItemStatus.CANCELED
			where i.order.id = :orderId
			""")
	int cancelItems(@Param("orderId") Long orderId);

	@Query("""
			select o.id from Order o
			where o.status = com.example.shopping.domain.order.entity.OrderStatus.PENDING and o.expiresAt < :now
			""")
	List<Long> findExpiredPendingIds(@Param("now") LocalDateTime now, Pageable pageable);

	@Query("""
			select o.id from Order o
			where o.status = com.example.shopping.domain.order.entity.OrderStatus.WAITING_DEPOSIT
			  and o.expiresAt <= :now
			order by o.expiresAt asc
			""")
	List<Long> findExpiredWaitingDepositIds(@Param("now") LocalDateTime now, Pageable pageable);

	@Query("""
			select o.id from Order o
			where o.status = com.example.shopping.domain.order.entity.OrderStatus.WAITING_DEPOSIT
			  and o.id > :afterId
			order by o.id asc
			""")
	List<Long> findWaitingDepositIdsAfter(@Param("afterId") Long afterId, Pageable pageable);

	/** 회원이 결제하지 않고 남겨 둔 주문 */
	@Query("""
			select o.id from Order o
			where o.memberId = :memberId
			  and o.status = com.example.shopping.domain.order.entity.OrderStatus.PENDING
			""")
	List<Long> findPendingIdsByMemberId(@Param("memberId") Long memberId);

	long countByMemberIdAndStatusIn(Long memberId, Collection<OrderStatus> statuses);

	/** 결제·배송·취소가 끝나지 않은 주문 수. 0이 아니면 탈퇴할 수 없다. */
	default long countOrdersBlockingWithdrawal(Long memberId) {
		return countByMemberIdAndStatusIn(memberId, OrderStatus.IN_PROGRESS);
	}

	/** 결제 승인을 시작한 뒤 결과가 반영되지 않은 채 멈춘 주문 */
	@Query("""
			select o.id from Order o
			where o.status = com.example.shopping.domain.order.entity.OrderStatus.PAYMENT_PROCESSING
			  and o.updatedAt < :before
			""")
	List<Long> findStuckProcessingIds(@Param("before") LocalDateTime before, Pageable pageable);

	/** 취소 요청 중 멈췄거나 과거에 취소 상태만 먼저 반영된 주문. */
	@Query("""
			select o.id from Order o
			where o.updatedAt < :before and (
			    o.status = com.example.shopping.domain.order.entity.OrderStatus.CANCEL_REQUESTED
			    or (o.status = com.example.shopping.domain.order.entity.OrderStatus.CANCELED and exists (
			        select p.id from Payment p where p.orderId = o.id
			          and p.status = com.example.shopping.domain.payment.entity.PaymentStatus.DONE
			    ))
			)
			order by o.updatedAt asc
			""")
	List<Long> findStuckCancellationIds(@Param("before") LocalDateTime before, Pageable pageable);
}
