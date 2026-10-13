package com.example.shopping.domain.cart.entity;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장바구니 품목. 가격은 저장하지 않고 조회할 때 상품의 현재 가격으로 계산한다. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "member_id", "sku_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

	public static final int MAX_QUANTITY = 99;
	public static final int MAX_KINDS = 50;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sku_id")
	private ProductSku sku;

	@Column(nullable = false)
	private int quantity;

	// 주문할 품목으로 선택했는지
	@Column(nullable = false)
	private boolean selected = true;

	public static CartItem create(Long memberId, ProductSku sku, int quantity) {
		CartItem item = new CartItem();
		item.memberId = memberId;
		item.sku = sku;
		item.changeQuantity(quantity);
		return item;
	}

	public void changeQuantity(int quantity) {
		if (quantity < 1 || quantity > MAX_QUANTITY) {
			throw new BusinessException(ErrorCode.CART_LIMIT_EXCEEDED);
		}
		this.quantity = quantity;
	}

	public void changeSelected(boolean selected) {
		this.selected = selected;
	}

	public CartItemStatus orderStatus() {
		Product product = sku.getProduct();
		if (product.getStatus() == ProductStatus.SOLD_OUT) {
			return CartItemStatus.OUT_OF_STOCK;
		}
		if (product.getStatus() != ProductStatus.ON_SALE) {
			return CartItemStatus.NOT_ON_SALE;
		}
		if (!product.isPriceVisible()) {
			return CartItemStatus.PRICE_HIDDEN;
		}
		if (sku.getStock() < quantity) {
			return CartItemStatus.OUT_OF_STOCK;
		}
		return CartItemStatus.ORDERABLE;
	}
}
