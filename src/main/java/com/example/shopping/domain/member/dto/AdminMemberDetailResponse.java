package com.example.shopping.domain.member.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.member.entity.Member;
import com.example.shopping.domain.member.entity.MemberRole;
import com.example.shopping.domain.member.entity.MemberStatus;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;

/** recentOrders는 최근 주문 20건 */
public record AdminMemberDetailResponse(Long id, String email, String name, String phone, MemberRole role,
		MemberStatus status, LocalDateTime createdAt, List<OrderSummary> recentOrders) {

	public record OrderSummary(Long id, String orderNo, OrderStatus status, String orderName, long totalAmount,
			LocalDateTime createdAt) {

		static OrderSummary from(Order order) {
			return new OrderSummary(order.getId(), order.getOrderNo(), order.getStatus(), order.getOrderName(),
					order.getTotalAmount(), order.getCreatedAt());
		}
	}

	public static AdminMemberDetailResponse of(Member member, List<Order> recentOrders) {
		return new AdminMemberDetailResponse(member.getId(), member.getEmail(), member.getName(), member.getPhone(),
				member.getRole(), member.getStatus(), member.getCreatedAt(),
				recentOrders.stream().map(OrderSummary::from).toList());
	}
}
