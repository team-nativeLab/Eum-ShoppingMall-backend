package com.example.shopping.domain.product.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.order.entity.OrderItem;
import com.example.shopping.domain.product.entity.Product;

/**
 * 리뷰를 쓸 수 있는 주문 상품. 리뷰를 쓸 때 orderItemId를 보낸다.
 *
 * @param productName 주문할 때의 상품 이름
 * @param optionLabel 주문할 때 고른 옵션 이름
 * @param imageUrl    지금의 상품 대표 이미지
 */
public record ReviewableItemResponse(
		Long orderItemId,
		String orderNo,
		LocalDateTime orderedAt,
		Long productId,
		String productName,
		String optionLabel,
		String imageUrl) {

	public static ReviewableItemResponse from(OrderItem item) {
		Product product = item.getSku().getProduct();
		return new ReviewableItemResponse(item.getId(), item.getOrder().getOrderNo(), item.getOrder().getCreatedAt(),
				product.getId(), item.getProductName(), item.getOptionName(), product.getImageUrl());
	}
}
