package com.example.shopping.domain.cart.dto.response;

import java.util.List;

import com.example.shopping.domain.cart.entity.CartItem;
import com.example.shopping.domain.cart.entity.CartItemStatus;
import com.example.shopping.domain.order.entity.ShippingPolicy;
import com.example.shopping.domain.product.entity.Product;
import com.example.shopping.domain.product.entity.ProductSku;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 장바구니와 예상 결제 금액
 *
 * @param items          담은 품목
 * @param orderableCount 선택한 품목 중 지금 주문할 수 있는 품목 수. 0이면 주문 버튼을 막는다.
 * @param itemAmount     선택했고 주문할 수 있는 품목의 금액 합계(원)
 * @param shippingFee    예상 배송비(원). 관리자가 정한 배송비 정책으로 계산한다.
 * @param paymentAmount  예상 결제 금액(원) = itemAmount + shippingFee
 * @param totalAmount    기존 클라이언트 호환용 itemAmount 별칭(원)
 */
public record CartResponse(List<Item> items, int orderableCount, long itemAmount,
		long shippingFee, long paymentAmount, long totalAmount) {

	/**
	 * price·amount는 금액 미표시 상품이면 null
	 *
	 * @param skuId        옵션(SKU) ID. 수량 변경·삭제 경로의 skuId
	 * @param productId    상품 ID
	 * @param productName  상품명
	 * @param optionName   옵션 이름
	 * @param imageUrl     상품 대표 이미지. 없으면 null
	 * @param quantity     담은 수량
	 * @param selected     주문할 품목으로 골랐는지
	 * @param priceVisible 금액을 공개하는 상품인지. false면 주문 대신 견적 문의로 안내한다.
	 * @param price        옵션 판매가(원). 주문에 그대로 쓰는 최종 단가
	 * @param amount       단가와 수량을 곱한 금액(원)
	 * @param status       지금 주문할 수 있는지. ORDERABLE(주문 가능), PRICE_HIDDEN(금액 미표시, 견적 문의), NOT_ON_SALE(판매하지 않음),
	 *                     OUT_OF_STOCK(품절이거나 재고 부족)
	 */
	@Schema(name = "CartItemResponse")
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

	public static CartResponse from(List<CartItem> cartItems, ShippingPolicy shippingPolicy) {
		List<Item> items = cartItems.stream().map(Item::from).toList();
		List<Item> toOrder = items.stream()
				.filter(item -> item.selected() && item.status() == CartItemStatus.ORDERABLE)
				.toList();
		long itemAmount = toOrder.stream().mapToLong(Item::amount).reduce(0, CartResponse::add);
		long shippingFee = shippingPolicy.shippingFeeFor(itemAmount);
		return new CartResponse(items, toOrder.size(), itemAmount, shippingFee, add(itemAmount, shippingFee),
				itemAmount);
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
