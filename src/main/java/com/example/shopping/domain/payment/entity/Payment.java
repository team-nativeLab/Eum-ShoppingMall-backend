package com.example.shopping.domain.payment.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Order와 양방향으로 묶지 않고 ID만 참조한다.
	@Column(name = "order_id", nullable = false)
	private Long orderId;

	// DB에 FK 제약(payment.order_id → orders.id)을 걸기 위한 읽기 전용 연관. 값은 위의 orderId로만 넣는다.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", insertable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_payment_order"))
	private Order order;

	@Column(nullable = false, unique = true, length = 200)
	private String paymentKey;

	@Column(nullable = false, length = 30)
	private String method;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private PaymentStatus status = PaymentStatus.READY;

	@Column(nullable = false)
	private long approvedAmount;

	// 취소 후 남은 금액
	@Column(nullable = false)
	private long balanceAmount;

	// PG 응답 원문
	@Column(columnDefinition = "TEXT")
	private String pgRaw;

	private LocalDateTime approvedAt;

	/** PG가 승인한 결과를 그대로 기록한다. */
	public static Payment of(Long orderId, PgPayment pg, PaymentStatus status) {
		Payment payment = new Payment();
		payment.orderId = orderId;
		payment.paymentKey = pg.paymentKey();
		payment.method = pg.method() == null ? "UNKNOWN" : pg.method();
		payment.status = status;
		payment.approvedAmount = pg.totalAmount();
		payment.balanceAmount = pg.balanceAmount();
		payment.pgRaw = pg.raw();
		payment.approvedAt = pg.approvedAt();
		return payment;
	}
}
