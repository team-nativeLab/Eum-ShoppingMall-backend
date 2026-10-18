package com.example.shopping.domain.order.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.order.entity.Order;

/**
 * 결제창을 띄울 때 필요한 값. 결제창의 orderId·amount에는 반드시 이 응답의 orderNo·amount를 그대로 넣는다.
 *
 * @param expiresAt 이 시각까지 결제하지 않으면 주문이 만료된다.
 */
public record OrderCreateResponse(String orderNo, String orderName, long amount, LocalDateTime expiresAt) {

	public static OrderCreateResponse from(Order order) {
		return new OrderCreateResponse(order.getOrderNo(), order.getOrderName(), order.getTotalAmount(),
				order.getExpiresAt());
	}
}
