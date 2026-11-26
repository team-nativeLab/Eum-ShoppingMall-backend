package com.example.shopping.domain.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.order.dto.response.AdminOrderDetailResponse.AddressResponse;
import com.example.shopping.domain.order.dto.response.AdminOrderDetailResponse.ItemResponse;
import com.example.shopping.domain.order.dto.response.AdminOrderDetailResponse.PaymentResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.payment.entity.Payment;

/**
 * 고객용 주문 상세. payment는 결제 전이면 null. 결제 대기 중에는 status로 결제 결과를 확인(폴링)한다.
 *
 * @param orderNo         주문번호
 * @param status          주문 상태. PENDING(결제 전), PAYMENT_PROCESSING(결제 확인 중), WAITING_DEPOSIT(입금 대기), PAID(결제 완료),
 *                        PREPARING(상품 준비중), CANCEL_REQUESTED(취소 처리 중), SHIPPED(배송중), DELIVERED(배송완료), CANCELED(취소),
 *                        EXPIRED(결제 기한 만료)
 * @param orderName       주문명(예: 안전모 외 2건)
 * @param itemAmount      상품 금액 합계(원)
 * @param discountAmount  할인 금액(원). 할인 기능이 없어 지금은 0
 * @param shippingFee     배송비(원)
 * @param totalAmount     결제 금액(원) = itemAmount - discountAmount + shippingFee
 * @param shippingAddress 배송지
 * @param courier         택배사. 발송 전이면 null
 * @param trackingNumber  송장번호. 발송 전이면 null
 * @param items           주문 품목
 * @param payment         최근 결제. 결제 전이면 null
 * @param createdAt       주문 시각
 * @param paidAt          결제 완료 시각. 결제 전이면 null
 * @param expiresAt       이 시각까지 결제하지 않으면 주문이 만료된다.
 */
public record OrderDetailResponse(
		String orderNo,
		OrderStatus status,
		String orderName,
		long itemAmount,
		long discountAmount,
		long shippingFee,
		long totalAmount,
		AddressResponse shippingAddress,
		String courier,
		String trackingNumber,
		List<ItemResponse> items,
		PaymentResponse payment,
		LocalDateTime createdAt,
		LocalDateTime paidAt,
		LocalDateTime expiresAt) {

	public static OrderDetailResponse of(Order order, Payment payment) {
		return new OrderDetailResponse(order.getOrderNo(), order.getStatus(), order.getOrderName(),
				order.getItemAmount(), order.getDiscountAmount(), order.getShippingFee(), order.getTotalAmount(),
				AddressResponse.from(order.getShippingAddress()), order.getCourier(), order.getTrackingNumber(),
				order.getItems().stream().map(ItemResponse::from).toList(), PaymentResponse.from(payment),
				order.getCreatedAt(), order.getPaidAt(), order.getExpiresAt());
	}
}
