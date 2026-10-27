package com.example.shopping.domain.cart.entity;

import com.example.shopping.domain.product.entity.ProductSku;
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

	/** 장바구니에 담을 수 있는 상품 종류 수. 수량에는 따로 상한을 두지 않는다 (주문할 때 재고로 판단한다). */
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

	/**
	 * 수량은 1개 이상이면 된다. 옵션 하나가 가질 수 있는 재고의 상한(ProductSku.MAX_STOCK)보다 많이 담는 것은
	 * 어떤 경우에도 주문할 수 없으므로 재고 부족으로 거절한다.
	 */
	public void changeQuantity(int quantity) {
		if (quantity < 1) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (quantity > ProductSku.MAX_STOCK) {
			throw new BusinessException(ErrorCode.OUT_OF_STOCK);
		}
		this.quantity = quantity;
	}

	/** 이미 담긴 옵션을 다시 담으면 수량을 더한다. 더한 값이 int 범위를 넘어도 음수가 되지 않게 한다. */
	public void addQuantity(int more) {
		changeQuantity((int) Math.min((long) quantity + more, Integer.MAX_VALUE));
	}

	public void changeSelected(boolean selected) {
		this.selected = selected;
	}

	public CartItemStatus orderStatus() {
		return CartItemStatus.of(sku, quantity);
	}
}
