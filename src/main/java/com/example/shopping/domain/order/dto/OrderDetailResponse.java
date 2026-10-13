package com.example.shopping.domain.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.order.dto.AdminOrderDetailResponse.AddressResponse;
import com.example.shopping.domain.order.dto.AdminOrderDetailResponse.ItemResponse;
import com.example.shopping.domain.order.dto.AdminOrderDetailResponse.PaymentResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.payment.entity.Payment;

/** 고객용 주문 상세. payment는 결제 전이면 null. 결제 대기 중에는 status로 결제 결과를 확인(폴링)한다. */
public record OrderDetailResponse(String orderNo, OrderStatus status, String orderName, long itemAmount,
		long discountAmount, long shippingFee, long totalAmount, AddressResponse shippingAddress, String courier,
		String trackingNumber, List<ItemResponse> items, PaymentResponse payment, LocalDateTime createdAt,
		LocalDateTime paidAt, LocalDateTime expiresAt) {

	public static OrderDetailResponse of(Order order, Payment payment) {
		return new OrderDetailResponse(order.getOrderNo(), order.getStatus(), order.getOrderName(),
				order.getItemAmount(), order.getDiscountAmount(), order.getShippingFee(), order.getTotalAmount(),
				AddressResponse.from(order.getShippingAddress()), order.getCourier(), order.getTrackingNumber(),
				order.getItems().stream().map(ItemResponse::from).toList(), PaymentResponse.from(payment),
				order.getCreatedAt(), order.getPaidAt(), order.getExpiresAt());
	}
}
