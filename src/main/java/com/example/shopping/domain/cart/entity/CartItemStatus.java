package com.example.shopping.domain.cart.entity;

/** 장바구니 품목을 지금 주문할 수 있는지. ORDERABLE이 아니면 주문에서 빠진다. */
public enum CartItemStatus {
	ORDERABLE,
	/** 금액 미표시 상품. 주문 대신 견적 문의로 안내한다. */
	PRICE_HIDDEN,
	/** 비공개·삭제 등으로 판매하지 않는 상품 */
	NOT_ON_SALE,
	/** 품절이거나 담은 수량보다 재고가 적음 */
	OUT_OF_STOCK
}
