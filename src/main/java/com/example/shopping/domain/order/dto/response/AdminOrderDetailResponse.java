package com.example.shopping.domain.order.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.history.dto.StatusHistoryResponse;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderItem;
import com.example.shopping.domain.order.entity.OrderItemStatus;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.order.entity.ShippingAddress;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.entity.PaymentStatus;

/**
 * shippingAddress는 배송지 입력 전이면 null, payment는 결제 시도 전이면 null
 *
 * @param id                  주문 ID
 * @param orderNo             주문번호
 * @param memberId            주문한 회원 ID
 * @param status              주문 상태. PENDING(결제 전), PAYMENT_PROCESSING(결제 확인 중), WAITING_DEPOSIT(입금 대기), PAID(결제 완료),
 *                            PREPARING(상품 준비중), CANCEL_REQUESTED(취소 처리 중), SHIPPED(배송중), DELIVERED(배송완료), CANCELED(취소),
 *                            EXPIRED(결제 기한 만료)
 * @param orderName           주문명(예: 안전모 외 2건)
 * @param itemAmount          상품 금액 합계(원)
 * @param discountAmount      할인 금액(원). 할인 기능이 없어 지금은 0
 * @param shippingFee         배송비(원)
 * @param totalAmount         결제 금액(원) = itemAmount - discountAmount + shippingFee
 * @param shippingAddress     배송지. 배송지 입력 전이면 null
 * @param courier             택배사. 발송 전이면 null
 * @param trackingNumber      송장번호. 발송 전이면 null
 * @param items               주문 품목
 * @param payment             최근 결제. 결제 시도 전이면 null
 * @param createdAt           주문 시각
 * @param paidAt              결제 완료 시각. 결제 전이면 null
 * @param cancelReason        취소 사유. 취소하지 않았으면 null
 * @param cancelledByMemberId 취소한 회원 ID(고객 본인이나 관리자). 취소하지 않았으면 null
 * @param cancelledAt         취소 시각. 취소하지 않았으면 null
 * @param adminMemo           고객에게 보이지 않는 내부 메모(#223)
 * @param statusHistory       상태가 바뀐 기록. 오래된 것부터(#223)
 */
public record AdminOrderDetailResponse(Long id, String orderNo, Long memberId, OrderStatus status, String orderName,
		long itemAmount, long discountAmount, long shippingFee, long totalAmount, AddressResponse shippingAddress,
		String courier, String trackingNumber, List<ItemResponse> items, PaymentResponse payment,
		LocalDateTime createdAt, LocalDateTime paidAt, String cancelReason, Long cancelledByMemberId,
		LocalDateTime cancelledAt, String adminMemo, List<StatusHistoryResponse> statusHistory) {

	/**
	 * 주문 배송지
	 *
	 * @param recipient       받는 분 이름
	 * @param recipientPhone  받는 분 연락처
	 * @param zipCode         우편번호
	 * @param address         주소
	 * @param addressDetail   상세 주소
	 * @param deliveryRequest 배송 요청 사항
	 */
	public record AddressResponse(String recipient, String recipientPhone, String zipCode, String address,
			String addressDetail, String deliveryRequest) {

		public static AddressResponse from(ShippingAddress address) {
			if (address == null) {
				return null;
			}
			return new AddressResponse(address.getRecipient(), address.getRecipientPhone(), address.getZipCode(),
					address.getAddress(), address.getAddressDetail(), address.getDeliveryRequest());
		}
	}

	/**
	 * 주문 품목. 이름과 가격은 주문할 때의 값이다.
	 *
	 * @param id          주문 품목 ID
	 * @param skuId       옵션(SKU) ID
	 * @param productName 주문할 때의 상품명
	 * @param optionName  주문할 때의 옵션 이름
	 * @param orderPrice  주문할 때의 단가(원)
	 * @param quantity    수량
	 * @param status      품목 상태. ORDERED(주문), CANCELED(취소), RETURNED(반품)
	 */
	public record ItemResponse(Long id, Long skuId, String productName, String optionName, long orderPrice,
			int quantity, OrderItemStatus status) {

		static ItemResponse from(OrderItem item) {
			return new ItemResponse(item.getId(), item.getSkuId(), item.getProductName(), item.getOptionName(),
					item.getOrderPrice(), item.getQuantity(), item.getStatus());
		}
	}

	/**
	 * 결제
	 *
	 * @param method         결제 수단(토스페이먼츠가 알려 준 이름, 예: 카드, 가상계좌). 모르면 UNKNOWN
	 * @param status         결제 상태. READY(결제 전), WAITING_DEPOSIT(입금 대기), DONE(완료), CANCELED(취소), FAILED(실패)
	 * @param approvedAmount 승인 금액(원)
	 * @param balanceAmount  취소하고 남은 금액(원)
	 * @param approvedAt     승인 시각. 승인 전이면 null
	 * @param virtualAccount 가상계좌. 가상계좌 결제가 아니면 null
	 */
	public record PaymentResponse(String method, PaymentStatus status, long approvedAmount, long balanceAmount,
			LocalDateTime approvedAt, VirtualAccountResponse virtualAccount) {

		/**
		 * 입금할 가상계좌
		 *
		 * @param bankCode      은행 코드(토스페이먼츠 기준)
		 * @param accountNumber 계좌번호
		 * @param customerName  입금자 이름
		 * @param dueDate       입금 기한
		 * @param expired       입금 기한이 지났는지
		 */
		public record VirtualAccountResponse(String bankCode, String accountNumber, String customerName,
				LocalDateTime dueDate, boolean expired) {
		}

		static PaymentResponse from(Payment payment) {
			if (payment == null) {
				return null;
			}
			VirtualAccountResponse virtualAccount = null;
			if (payment.getStatus() == PaymentStatus.WAITING_DEPOSIT && payment.getVirtualAccountDueAt() != null) {
				virtualAccount = new VirtualAccountResponse(payment.getVirtualAccountBankCode(),
						payment.getVirtualAccountNumber(), payment.getVirtualAccountCustomerName(),
						payment.getVirtualAccountDueAt(),
						!payment.getVirtualAccountDueAt().isAfter(LocalDateTime.now()));
			}
			return new PaymentResponse(payment.getMethod(), payment.getStatus(), payment.getApprovedAmount(),
					payment.getBalanceAmount(), payment.getApprovedAt(), virtualAccount);
		}
	}

	public static AdminOrderDetailResponse of(Order order, Payment payment, List<StatusHistoryResponse> statusHistory) {
		return new AdminOrderDetailResponse(order.getId(), order.getOrderNo(), order.getMemberId(), order.getStatus(),
				order.getOrderName(), order.getItemAmount(), order.getDiscountAmount(), order.getShippingFee(),
				order.getTotalAmount(), AddressResponse.from(order.getShippingAddress()), order.getCourier(),
				order.getTrackingNumber(), order.getItems().stream().map(ItemResponse::from).toList(),
				PaymentResponse.from(payment), order.getCreatedAt(), order.getPaidAt(), order.getCancelReason(),
				order.getCancelledByMemberId(), order.getCancelledAt(), order.getAdminMemo(), statusHistory);
	}
}
