package com.example.shopping.domain.order.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;

public record OrderSummaryResponse(String orderNo, OrderStatus status, String orderName, long totalAmount,
		LocalDateTime createdAt) {

	public static OrderSummaryResponse from(Order order) {
		return new OrderSummaryResponse(order.getOrderNo(), order.getStatus(), order.getOrderName(),
				order.getTotalAmount(), order.getCreatedAt());
	}
}
