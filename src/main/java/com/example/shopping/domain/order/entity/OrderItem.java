package com.example.shopping.domain.order.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.shopping.domain.product.entity.ProductSku;

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

/** 상품명·옵션명·단가는 주문 시점 스냅샷이다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id")
	private Order order;

	@Column(name = "sku_id", nullable = false)
	private Long skuId;

	// DB에 FK 제약(order_item.sku_id → product_sku.id)을 걸기 위한 읽기 전용 연관. 값은 위의 skuId로만 넣는다.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sku_id", insertable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_oi_sku"))
	private ProductSku sku;

	@Column(nullable = false, length = 200)
	private String productName;

	@Column(nullable = false, length = 100)
	private String optionName;

	@Column(nullable = false)
	private long orderPrice;

	@Column(nullable = false)
	private int quantity;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private OrderItemStatus status = OrderItemStatus.ORDERED;

	static OrderItem create(Order order, Long skuId, String productName, String optionName, long orderPrice,
			int quantity) {
		OrderItem item = new OrderItem();
		item.order = order;
		item.skuId = skuId;
		item.productName = productName;
		item.optionName = optionName;
		item.orderPrice = orderPrice;
		item.quantity = quantity;
		return item;
	}
}
