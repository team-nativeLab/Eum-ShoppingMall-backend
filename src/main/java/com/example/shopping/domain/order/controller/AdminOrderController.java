package com.example.shopping.domain.order.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.example.shopping.domain.order.dto.request.AdminOrderCancelRequest;
import com.example.shopping.domain.order.dto.response.AdminOrderDetailResponse;
import com.example.shopping.domain.order.dto.response.AdminOrderSummaryResponse;
import com.example.shopping.domain.order.dto.request.AdminOrderUpdateRequest;
import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.domain.order.service.AdminOrderService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.response.PageResponse;
import com.example.shopping.global.security.CurrentMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

	private final AdminOrderService adminOrderService;

	/** ADM-010 전체 주문 목록·검색 (?status=&memberId=&orderNo=). 주문번호는 전체나 앞부분(예: 주문일 20261028)으로 찾는다. */
	@GetMapping
	public ApiResponse<PageResponse<AdminOrderSummaryResponse>> orders(
			@RequestParam(required = false) OrderStatus status,
			@RequestParam(required = false) Long memberId,
			@RequestParam(required = false) String orderNo,
			@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.ok(adminOrderService.search(status, memberId, orderNo, pageable));
	}

	/** ADM-011 주문 상세 (품목·배송지·결제 정보) */
	@GetMapping("/{orderId}")
	public ApiResponse<AdminOrderDetailResponse> order(@PathVariable Long orderId) {
		return ApiResponse.ok(adminOrderService.getDetail(orderId));
	}

	/** ADM-011 주문 상태 변경·송장 등록 */
	@PatchMapping("/{orderId}")
	public ApiResponse<AdminOrderDetailResponse> updateOrder(@PathVariable Long orderId,
			@Valid @RequestBody AdminOrderUpdateRequest request) {
		return ApiResponse.ok(adminOrderService.update(orderId, request));
	}

	/** 결제 환불 또는 결제 대기 주문 만료를 처리한다. */
	@PostMapping("/{orderId}/cancel")
	public ApiResponse<AdminOrderDetailResponse> cancelOrder(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long orderId, @Valid @RequestBody AdminOrderCancelRequest request) {
		adminOrderService.cancel(orderId, CurrentMember.id(jwt), request.reason());
		return ApiResponse.ok(adminOrderService.getDetail(orderId));
	}
}
