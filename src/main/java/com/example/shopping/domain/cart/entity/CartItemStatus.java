package com.example.shopping.domain.cart.entity;

import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;
import com.example.shopping.domain.product.entity.ProductStatus;

/** 옵션을 이 수량만큼 지금 주문할 수 있는지. 장바구니 표시와 주문 생성이 같은 기준을 쓴다. */
public enum CartItemStatus {
	ORDERABLE,
	/** 금액 미표시 상품. 주문 대신 견적 문의로 안내한다. */
	PRICE_HIDDEN,
	/** 비공개·삭제 등으로 판매하지 않는 상품 */
	NOT_ON_SALE,
	/** 품절이거나 담은 수량보다 재고가 적음 */
	OUT_OF_STOCK;

	public static CartItemStatus of(ProductSku sku, int quantity) {
		Product product = sku.getProduct();
		if (!sku.isActive()) {
			return NOT_ON_SALE;
		}
		if (product.getStatus() == ProductStatus.SOLD_OUT) {
			return OUT_OF_STOCK;
		}
		if (product.getStatus() != ProductStatus.ON_SALE) {
			return NOT_ON_SALE;
		}
		if (!product.isPriceVisible()) {
			return PRICE_HIDDEN;
		}
		if (sku.getStock() < quantity) {
			return OUT_OF_STOCK;
		}
		return ORDERABLE;
	}
}
