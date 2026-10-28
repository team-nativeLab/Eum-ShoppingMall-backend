package com.example.shopping.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

	@Test
	void 끝난_주문은_더_바뀌지_않는다() {
		for (OrderStatus terminal : EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELED, OrderStatus.EXPIRED)) {
			for (OrderStatus next : OrderStatus.values()) {
				assertThat(terminal.canTransitTo(next)).as(terminal + " -> " + next).isFalse();
			}
		}
	}

	@Test
	void 표에_없는_전이를_하려고_하면_바로_실패한다() {
		assertThatCode(() -> OrderStatus.requireTransitions(Set.of(OrderStatus.PAID, OrderStatus.PREPARING),
				OrderStatus.CANCEL_REQUESTED)).doesNotThrowAnyException();
		assertThatThrownBy(() -> OrderStatus.requireTransitions(Set.of(OrderStatus.PAID, OrderStatus.SHIPPED),
				OrderStatus.CANCEL_REQUESTED))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SHIPPED -> CANCEL_REQUESTED");
	}

	@Test
	void 진행_중인_주문에는_취소_결과를_기다리는_주문도_들어간다() {
		assertThat(OrderStatus.IN_PROGRESS).contains(OrderStatus.CANCEL_REQUESTED)
				.doesNotContain(OrderStatus.DELIVERED, OrderStatus.CANCELED, OrderStatus.EXPIRED);
	}
}
