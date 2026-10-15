package com.example.shopping.domain.order.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.global.entity.BaseTimeEntity;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = { "member_id", "idempotency_key" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

	private static final int ORDER_NAME_MAX_LENGTH = 200;

	/** 한 주문의 최대 결제 금액(1조 원). 수량에 상한이 없으므로 금액 계산이 long 범위를 넘지 않게 막는 마지막 선이다. */
	public static final long MAX_TOTAL_AMOUNT = ValidationPatterns.MAX_PRICE;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 서버가 생성하는 추측 불가 주문번호. PG의 orderId로도 쓴다.
	@Column(nullable = false, unique = true, length = 40)
	private String orderNo;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	// DB에 FK 제약(orders.member_id → member.id)을 걸기 위한 읽기 전용 연관. 값은 위의 memberId로만 넣는다.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", insertable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_orders_member"))
	private Member member;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private OrderStatus status = OrderStatus.PENDING;

	// 최종 결제 금액 = itemAmount - discountAmount + shippingFee (서버 계산값만 신뢰)
	@Column(nullable = false)
	private long totalAmount;

	@Column(nullable = false)
	private long itemAmount;

	@Column(nullable = false)
	private long discountAmount;

	@Column(nullable = false)
	private long shippingFee;

	@Column(nullable = false, length = ORDER_NAME_MAX_LENGTH)
	private String orderName;

	@Embedded
	private ShippingAddress shippingAddress;

	// 이 시각까지 결제하지 않으면 주문을 만료시키고 잡아 둔 재고를 되돌린다.
	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime paidAt;

	// 관리자가 배송 처리할 때 등록하는 송장 정보
	@Column(length = 50)
	private String courier;

	@Column(length = 50)
	private String trackingNumber;

	// 주문 버튼을 여러 번 눌러도 주문이 하나만 생기도록 프론트가 보내는 키. 회원별로 유일하다.
	@Column(name = "idempotency_key", length = 100)
	private String idempotencyKey;

	@Version
	private long version;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	public static Order create(Long memberId, String orderNo, ShippingAddress shippingAddress,
			LocalDateTime expiresAt, String idempotencyKey) {
		Order order = new Order();
		order.memberId = memberId;
		order.orderNo = orderNo;
		order.shippingAddress = shippingAddress;
		order.expiresAt = expiresAt;
		order.idempotencyKey = idempotencyKey;
		return order;
	}

	/** 주문 시점의 상품명·옵션명·단가를 그대로 남긴다. */
	public void addItem(Long skuId, String productName, String optionName, long price, int quantity) {
		items.add(OrderItem.create(this, skuId, productName, optionName, price, quantity));
	}

	/** 품목을 다 담은 뒤 금액과 주문명을 확정한다. 금액이 상한을 넘으면 거절한다. */
	public void finalizeAmounts(long discountAmount, long shippingFee) {
		try {
			long sum = 0;
			for (OrderItem item : items) {
				sum = Math.addExact(sum, Math.multiplyExact(item.getOrderPrice(), item.getQuantity()));
			}
			this.itemAmount = sum;
			this.totalAmount = Math.addExact(Math.subtractExact(sum, discountAmount), shippingFee);
		} catch (ArithmeticException e) {
			throw new BusinessException(ErrorCode.ORDER_AMOUNT_TOO_LARGE);
		}
		if (totalAmount > MAX_TOTAL_AMOUNT) {
			throw new BusinessException(ErrorCode.ORDER_AMOUNT_TOO_LARGE);
		}
		this.discountAmount = discountAmount;
		this.shippingFee = shippingFee;

		String suffix = items.size() > 1 ? " 외 " + (items.size() - 1) + "건" : "";
		String firstName = items.get(0).getProductName();
		int maxNameLength = ORDER_NAME_MAX_LENGTH - suffix.length();
		this.orderName = (firstName.length() > maxNameLength ? firstName.substring(0, maxNameLength) : firstName)
				+ suffix;
	}

	public boolean isExpired(LocalDateTime now) {
		return status == OrderStatus.EXPIRED || (status == OrderStatus.PENDING && expiresAt.isBefore(now));
	}

	/** 상태 머신(OrderStatus)이 허용하는 전이만 받아들인다. */
	public void changeStatus(OrderStatus next) {
		if (!status.canTransitTo(next)) {
			throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
		}
		this.status = next;
	}

	/** null인 항목은 바꾸지 않는다. */
	public void registerInvoice(String courier, String trackingNumber) {
		if (courier != null) {
			this.courier = courier;
		}
		if (trackingNumber != null) {
			this.trackingNumber = trackingNumber;
		}
	}
}
