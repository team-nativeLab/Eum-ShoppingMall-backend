package com.example.shopping.domain.order.entity;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
	PENDING, PAYMENT_PROCESSING, WAITING_DEPOSIT, PAID,
	PREPARING, SHIPPED, DELIVERED,
	PARTIAL_CANCELED, CANCELED, EXPIRED;

	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
			PENDING, Set.of(PAYMENT_PROCESSING, WAITING_DEPOSIT, EXPIRED),
			PAYMENT_PROCESSING, Set.of(PAID, PENDING, WAITING_DEPOSIT),
			WAITING_DEPOSIT, Set.of(PAID, EXPIRED),
			PAID, Set.of(PREPARING, PARTIAL_CANCELED, CANCELED),
			PREPARING, Set.of(SHIPPED, PARTIAL_CANCELED, CANCELED),
			SHIPPED, Set.of(DELIVERED),
			DELIVERED, Set.of(PARTIAL_CANCELED, CANCELED),
			PARTIAL_CANCELED, Set.of(PARTIAL_CANCELED, CANCELED));

	public boolean canTransitTo(OrderStatus next) {
		return ALLOWED.getOrDefault(this, Set.of()).contains(next);
	}
}
