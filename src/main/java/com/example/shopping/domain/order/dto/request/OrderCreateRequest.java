package com.example.shopping.domain.order.dto.request;

import java.util.List;

import com.example.shopping.domain.cart.entity.CartItem;
import com.example.shopping.domain.product.entity.ProductSku;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 주문할 품목과 배송지를 한 번에 보낸다. 서버는 요청에 담긴 내용만으로 주문을 만들고, 금액은 DB의 현재 가격으로 계산한다.
 *
 * @param items    주문할 옵션과 수량. 장바구니에 없는 옵션도 된다(바로 구매). 같은 옵션을 두 번 넣으면 400
 * @param delivery 배송지와 배송 요청 사항
 */
public record OrderCreateRequest(
		@NotEmpty(message = "주문할 상품을 선택해 주세요.")
		@Size(max = CartItem.MAX_KINDS, message = "한 번에 50종까지 주문할 수 있습니다.")
		List<@NotNull(message = "주문할 상품을 확인해 주세요.") @Valid Item> items,

		@NotNull(message = "배송지를 입력해 주세요.")
		@Valid
		DeliveryRequest delivery) {

	public record Item(
			@NotNull(message = "옵션을 선택해 주세요.")
			Long skuId,

			@NotNull(message = "수량을 입력해 주세요.")
			@Positive(message = "수량은 1개 이상이어야 합니다.")
			@Max(value = ProductSku.MAX_STOCK, message = "재고가 부족합니다.")
			Integer quantity) {
	}
}
