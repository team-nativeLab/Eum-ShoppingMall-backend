package com.example.shopping.domain.cart.dto;

import java.util.List;

import com.example.shopping.domain.cart.entity.CartItem;
import com.example.shopping.domain.cart.entity.CartItemStatus;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;

/**
 * @param orderableCount 선택한 품목 중 지금 주문할 수 있는 품목 수. 0이면 주문 버튼을 막는다.
 * @param totalAmount    선택했고 주문할 수 있는 품목의 금액 합계(원)
 */
public record CartResponse(List<Item> items, int orderableCount, long totalAmount) {

	/** price·amount는 금액 미표시 상품이면 null */
	public record Item(Long skuId, Long productId, String productName, String optionName, String imageUrl,
			int quantity, boolean selected, boolean priceVisible, Long price, Long amount, CartItemStatus status) {

		static Item from(CartItem item) {
			ProductSku sku = item.getSku();
			Product product = sku.getProduct();
			boolean priceVisible = product.isPriceVisible();
			return new Item(sku.getId(), product.getId(), product.getName(), sku.getOptionName(),
					product.getImageUrl(), item.getQuantity(), item.isSelected(), priceVisible,
					priceVisible ? sku.getPrice() : null,
					priceVisible ? sku.getPrice() * item.getQuantity() : null,
					item.orderStatus());
		}
	}

	public static CartResponse from(List<CartItem> cartItems) {
		List<Item> items = cartItems.stream().map(Item::from).toList();
		List<Item> toOrder = items.stream()
				.filter(item -> item.selected() && item.status() == CartItemStatus.ORDERABLE)
				.toList();
		return new CartResponse(items, toOrder.size(), toOrder.stream().mapToLong(Item::amount).sum());
	}
}
