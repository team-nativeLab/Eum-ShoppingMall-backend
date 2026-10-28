package com.example.shopping.domain.payment.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.payment.gateway.PgPayment;
import com.example.shopping.domain.payment.gateway.PgVirtualAccount;
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

	/** Toss가 가상계좌 결제에 주는 결제 수단 이름 */
	public static final String VIRTUAL_ACCOUNT_METHOD = "가상계좌";

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

	@Column(name = "virtual_account_bank_code", length = 10)
	private String virtualAccountBankCode;

	@Column(name = "virtual_account_number", length = 20)
	private String virtualAccountNumber;

	@Column(name = "virtual_account_customer_name", length = 100)
	private String virtualAccountCustomerName;

	@Column(name = "virtual_account_due_at")
	private LocalDateTime virtualAccountDueAt;

	@Column(name = "webhook_secret_hash", length = 64)
	private String webhookSecretHash;

	/** PG가 승인한 결과를 그대로 기록한다. */
	public static Payment of(Long orderId, PgPayment pg, PaymentStatus status) {
		Payment payment = new Payment();
		payment.orderId = orderId;
		payment.paymentKey = pg.paymentKey();
		payment.method = pg.method() == null ? "UNKNOWN" : pg.method();
		payment.status = status;
		payment.approvedAmount = pg.totalAmount();
		payment.balanceAmount = pg.balanceAmount();
		// 가상계좌 응답에는 웹훅 검증 비밀값이 포함되므로 원문 전체를 DB에 보관하지 않는다.
		payment.pgRaw = pg.secret() == null ? pg.raw() : null;
		payment.approvedAt = pg.approvedAt();
		PgVirtualAccount virtualAccount = pg.virtualAccount();
		if (virtualAccount != null) {
			payment.virtualAccountBankCode = virtualAccount.bankCode();
			payment.virtualAccountNumber = virtualAccount.accountNumber();
			payment.virtualAccountCustomerName = virtualAccount.customerName();
			payment.virtualAccountDueAt = virtualAccount.dueDate();
		}
		payment.webhookSecretHash = hashWebhookSecret(pg.secret());
		return payment;
	}

	/** 입금 상태 변경은 PG 조회 결과를 검증한 서비스에서만 호출한다. */
	public void recordDeposit(PgPayment pg) {
		if (status != PaymentStatus.WAITING_DEPOSIT) {
			throw new IllegalStateException("가상계좌 입금 대기 결제가 아닙니다.");
		}
		status = PaymentStatus.DONE;
		balanceAmount = pg.balanceAmount();
		pgRaw = pg.secret() == null ? pg.raw() : null;
	}

	public void expireVirtualAccount() {
		if (status != PaymentStatus.WAITING_DEPOSIT) {
			throw new IllegalStateException("가상계좌 입금 대기 결제가 아닙니다.");
		}
		status = PaymentStatus.FAILED;
	}

	public void revertDeposit(PgPayment pg) {
		if (status != PaymentStatus.DONE) {
			throw new IllegalStateException("입금 완료된 가상계좌 결제가 아닙니다.");
		}
		status = PaymentStatus.WAITING_DEPOSIT;
		balanceAmount = pg.balanceAmount();
		pgRaw = pg.secret() == null ? pg.raw() : null;
	}

	public boolean isVirtualAccount() {
		return VIRTUAL_ACCOUNT_METHOD.equals(method);
	}

	public boolean matchesWebhookSecret(String secret) {
		if (webhookSecretHash == null || secret == null) {
			return false;
		}
		return MessageDigest.isEqual(webhookSecretHash.getBytes(StandardCharsets.US_ASCII),
				hashWebhookSecret(secret).getBytes(StandardCharsets.US_ASCII));
	}

	private static String hashWebhookSecret(String secret) {
		if (secret == null) {
			return null;
		}
		try {
			return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
					.digest(secret.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}
}
