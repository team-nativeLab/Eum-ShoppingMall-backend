package com.example.shopping.domain.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderItem;
import com.example.shopping.domain.order.entity.OrderItemStatus;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.order.entity.ShippingAddress;
import com.example.shopping.domain.payment.entity.Payment;
import com.example.shopping.domain.payment.entity.PaymentStatus;

/** shippingAddress는 배송지 입력 전이면 null, payment는 결제 시도 전이면 null */
public record AdminOrderDetailResponse(Long id, String orderNo, Long memberId, OrderStatus status, String orderName,
		long itemAmount, long discountAmount, long shippingFee, long totalAmount, AddressResponse shippingAddress,
		String courier, String trackingNumber, List<ItemResponse> items, PaymentResponse payment,
		LocalDateTime createdAt, LocalDateTime paidAt) {

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

	public record ItemResponse(Long id, Long skuId, String productName, String optionName, long orderPrice,
			int quantity, OrderItemStatus status) {

		static ItemResponse from(OrderItem item) {
			return new ItemResponse(item.getId(), item.getSkuId(), item.getProductName(), item.getOptionName(),
					item.getOrderPrice(), item.getQuantity(), item.getStatus());
		}
	}

	public record PaymentResponse(String method, PaymentStatus status, long approvedAmount, long balanceAmount,
			LocalDateTime approvedAt, VirtualAccountResponse virtualAccount) {

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

	public static AdminOrderDetailResponse of(Order order, Payment payment) {
		return new AdminOrderDetailResponse(order.getId(), order.getOrderNo(), order.getMemberId(), order.getStatus(),
				order.getOrderName(), order.getItemAmount(), order.getDiscountAmount(), order.getShippingFee(),
				order.getTotalAmount(), AddressResponse.from(order.getShippingAddress()), order.getCourier(),
				order.getTrackingNumber(), order.getItems().stream().map(ItemResponse::from).toList(),
				PaymentResponse.from(payment), order.getCreatedAt(), order.getPaidAt());
	}
}
