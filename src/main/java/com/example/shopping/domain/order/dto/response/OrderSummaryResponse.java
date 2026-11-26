package com.example.shopping.domain.order.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;

/**
 * 내 주문 목록의 한 줄
 *
 * @param orderNo     주문번호. 상세 조회 경로의 orderNo
 * @param status      주문 상태. PENDING(결제 전), PAYMENT_PROCESSING(결제 확인 중), WAITING_DEPOSIT(입금 대기), PAID(결제 완료),
 *                    PREPARING(상품 준비중), CANCEL_REQUESTED(취소 처리 중), SHIPPED(배송중), DELIVERED(배송완료), CANCELED(취소),
 *                    EXPIRED(결제 기한 만료)
 * @param orderName   주문명(예: 안전모 외 2건)
 * @param totalAmount 결제 금액(원)
 * @param createdAt   주문 시각
 */
public record OrderSummaryResponse(String orderNo, OrderStatus status, String orderName, long totalAmount,
		LocalDateTime createdAt) {

	public static OrderSummaryResponse from(Order order) {
		return new OrderSummaryResponse(order.getOrderNo(), order.getStatus(), order.getOrderName(),
				order.getTotalAmount(), order.getCreatedAt());
	}
}
