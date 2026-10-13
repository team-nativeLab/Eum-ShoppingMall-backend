package com.example.shopping.domain.order.service;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.shopping.domain.order.dto.AdminOrderDetailResponse;
import com.example.shopping.domain.order.dto.AdminOrderSummaryResponse;
import com.example.shopping.domain.order.dto.AdminOrderUpdateRequest;
import com.example.shopping.domain.order.entity.Order;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.order.repository.OrderRepository;
import com.example.shopping.domain.payment.repository.PaymentRepository;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.validation.SortValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderService {

	/**
	 * 관리자가 직접 지정할 수 있는 상태는 배송 진행 단계뿐이다.
	 * 결제완료·취소·만료는 결제 승인, 환불, 재고 복구가 함께 일어나야 하므로 각 기능의 처리 흐름에서만 바뀐다.
	 */
	private static final Set<OrderStatus> FULFILLMENT_STATUSES = Set.of(
			OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

	private static final Set<String> SORTABLE = Set.of("id", "createdAt", "paidAt", "totalAmount");

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;

	public PageResponse<AdminOrderSummaryResponse> search(OrderStatus status, Long memberId, String orderNo,
			Pageable pageable) {
		SortValidator.requireAllowed(pageable, SORTABLE);
		String trimmed = StringUtils.hasText(orderNo) ? orderNo.trim() : null;
		return PageResponse.from(orderRepository.search(status, memberId, trimmed, pageable)
				.map(AdminOrderSummaryResponse::from));
	}

	public AdminOrderDetailResponse getDetail(Long orderId) {
		return toDetail(find(orderId));
	}

	@Transactional
	public AdminOrderDetailResponse update(Long orderId, AdminOrderUpdateRequest request) {
		Order order = find(orderId);

		if (request.status() != null) {
			if (!FULFILLMENT_STATUSES.contains(request.status())) {
				throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
			}
			order.changeStatus(request.status());
		}

		if (request.courier() != null || request.trackingNumber() != null) {
			// 결제 전이거나 취소된 주문에는 송장을 등록할 수 없다.
			if (!FULFILLMENT_STATUSES.contains(order.getStatus())) {
				throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
			}
			order.registerInvoice(trim(request.courier()), trim(request.trackingNumber()));
		}
		return toDetail(order);
	}

	private AdminOrderDetailResponse toDetail(Order order) {
		return AdminOrderDetailResponse.of(order,
				paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null));
	}

	private Order find(Long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	private String trim(String value) {
		return value == null ? null : value.trim();
	}
}
