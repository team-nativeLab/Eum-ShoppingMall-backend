package com.example.shopping.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shopping.domain.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByPaymentKey(String paymentKey);

	/** 결제를 다시 시도한 주문은 결제 건이 여러 개일 수 있어 가장 최근 것을 쓴다. */
	Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);

	/** 결제 완료 건만 취소 처리한다. 1을 돌려받은 요청만 재고 복구 등 후속 처리를 한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Payment p
			set p.status = com.example.shopping.domain.payment.entity.PaymentStatus.CANCELED, p.balanceAmount = 0
			where p.id = :id and p.status = com.example.shopping.domain.payment.entity.PaymentStatus.DONE
			""")
	int markCanceled(@Param("id") Long id);
}
