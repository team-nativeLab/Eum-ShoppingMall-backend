package com.example.shopping.domain.cart.dto;

import java.util.List;

import com.example.shopping.domain.cart.entity.CartItem;
import com.example.shopping.domain.cart.entity.CartItemStatus;
import com.example.shopping.domain.order.OrderAmounts;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;

/**
 * @param orderableCount 선택한 품목 중 지금 주문할 수 있는 품목 수. 0이면 주문 버튼을 막는다.
 * @param itemAmount     선택했고 주문할 수 있는 품목의 금액 합계(원)
 * @param discountAmount 예상 할인 금액(원)
 * @param shippingFee    예상 배송비(원)
 * @param paymentAmount  예상 결제 금액(원)
 * @param totalAmount    기존 클라이언트 호환용 itemAmount 별칭(원)
 */
public record CartResponse(List<Item> items, int orderableCount, long itemAmount,
		long discountAmount, long shippingFee, long paymentAmount, long totalAmount) {

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
					priceVisible ? multiply(sku.getPrice(), item.getQuantity()) : null,
					item.orderStatus());
		}
	}

	public static CartResponse from(List<CartItem> cartItems) {
		List<Item> items = cartItems.stream().map(Item::from).toList();
		List<Item> toOrder = items.stream()
				.filter(item -> item.selected() && item.status() == CartItemStatus.ORDERABLE)
				.toList();
		long itemAmount = toOrder.stream().mapToLong(Item::amount).reduce(0, CartResponse::add);
		OrderAmounts amounts = OrderAmounts.calculate(itemAmount);
		return new CartResponse(items, toOrder.size(), itemAmount, amounts.discountAmount(), amounts.shippingFee(),
				amounts.totalAmount(), itemAmount);
	}

	/*
	 * 수량에 상한이 없어 금액이 long 범위를 넘을 수 있다. 넘으면 음수로 뒤집히지 않도록 최댓값으로 표시하고,
	 * 실제 주문은 주문 금액 상한(Order.MAX_TOTAL_AMOUNT)에서 거절된다.
	 */
	private static long multiply(long price, int quantity) {
		try {
			return Math.multiplyExact(price, quantity);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private static long add(long a, long b) {
		try {
			return Math.addExact(a, b);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}
}
