package com.example.shopping.domain.order.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;

public record AdminOrderSummaryResponse(Long id, String orderNo, Long memberId, OrderStatus status, String orderName,
		long totalAmount, LocalDateTime createdAt, LocalDateTime paidAt) {

	public static AdminOrderSummaryResponse from(Order order) {
		return new AdminOrderSummaryResponse(order.getId(), order.getOrderNo(), order.getMemberId(),
				order.getStatus(), order.getOrderName(), order.getTotalAmount(), order.getCreatedAt(),
				order.getPaidAt());
	}
}
