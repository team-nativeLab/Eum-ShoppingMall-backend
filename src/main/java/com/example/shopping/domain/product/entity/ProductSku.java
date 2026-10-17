package com.example.shopping.domain.product.entity;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 옵션 단위 상품. 재고는 SKU 단위로 관리한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSku {

	/** 옵션 하나가 가질 수 있는 재고 상한 (10억 개). 입고가 거듭돼 수량이 정수 범위를 넘는 일을 막는다. */
	public static final int MAX_STOCK = 1_000_000_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(nullable = false, length = 100)
	private String optionName;

	@Column(nullable = false)
	private long price;

	@Column(nullable = false)
	private int stock;

	@Column(name = "is_active", nullable = false)
	@ColumnDefault("true")
	private boolean active = true;

	@Version
	private long version;

	static ProductSku create(Product product, String optionName, long price, int stock) {
		ProductSku sku = new ProductSku();
		sku.product = product;
		sku.optionName = optionName;
		sku.price = price;
		sku.stock = stock;
		return sku;
	}

	public void update(String optionName, long price) {
		this.optionName = optionName;
		this.price = price;
	}

	public void deactivate() {
		this.active = false;
	}
}
