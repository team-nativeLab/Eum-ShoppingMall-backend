package com.example.shopping.domain.order.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.order.dto.request.OrderCreateRequest;
import com.example.shopping.domain.order.dto.response.OrderCreateResponse;
import com.example.shopping.domain.order.dto.response.OrderDetailResponse;
import com.example.shopping.domain.order.dto.response.OrderSummaryResponse;
import com.example.shopping.domain.order.service.OrderCancelService;
import com.example.shopping.domain.order.service.OrderService;
import com.example.shopping.global.exception.BusinessException;
import com.example.shopping.global.exception.ErrorCode;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

	private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

	private final OrderService orderService;
	private final OrderCancelService orderCancelService;

	/**
	 * WEB-015, 016 주문 생성. 주문할 옵션·수량과 배송지를 함께 보낸다 (금액은 서버가 계산한다).
	 * 주문 버튼을 여러 번 눌러도 주문이 하나만 생기도록 Idempotency-Key 헤더에 주문 시도마다 새 UUID를 넣어 보낸다.
	 */
	@PostMapping("/orders")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<OrderCreateResponse> createOrder(@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody OrderCreateRequest request) {
		if (idempotencyKey != null && idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		String key = StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
		return ApiResponse.ok(orderService.createOrder(CurrentMember.id(jwt), key, request));
	}

	/** WEB-020 내 주문 목록 */
	@GetMapping("/orders")
	public ApiResponse<PageResponse<OrderSummaryResponse>> orders(@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(orderService.getMyOrders(CurrentMember.id(jwt), pageable));
	}

	/** WEB-017 주문 완료·상세. 본인 주문만 조회된다. */
	@GetMapping("/orders/{orderNo}")
	public ApiResponse<OrderDetailResponse> order(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNo) {
		return ApiResponse.ok(orderService.getMyOrder(CurrentMember.id(jwt), orderNo));
	}

	/** 주문 취소. 배송 전(결제완료·상품준비중)인 본인 주문을 전액 환불한다. */
	@PostMapping("/orders/{orderNo}/cancel")
	public ApiResponse<OrderDetailResponse> cancelOrder(@AuthenticationPrincipal Jwt jwt,
			@PathVariable String orderNo) {
		return ApiResponse.ok(orderCancelService.cancel(CurrentMember.id(jwt), orderNo));
	}
}
