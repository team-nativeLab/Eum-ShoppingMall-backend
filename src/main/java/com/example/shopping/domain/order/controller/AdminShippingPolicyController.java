package com.example.shopping.domain.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.order.dto.request.ShippingPolicyRequest;
import com.example.shopping.domain.order.dto.response.ShippingPolicyResponse;
import com.example.shopping.domain.order.service.ShippingPolicyService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "배송비 정책 관리")
@RestController
@RequestMapping("/api/v1/admin/shipping-policy")
@RequiredArgsConstructor
public class AdminShippingPolicyController {

	private final ShippingPolicyService shippingPolicyService;

	/** 지금 적용하는 배송비 정책. 저장하기 전에는 기본값(10만 원 미만 5,000원) */
	@GetMapping
	public ApiResponse<ShippingPolicyResponse> get() {
		return ApiResponse.ok(shippingPolicyService.get());
	}

	/** 배송비 정책 변경. 이후 장바구니와 새 주문에 적용하고, 이미 만든 주문은 그대로다. */
	@PutMapping
	public ApiResponse<ShippingPolicyResponse> update(@Valid @RequestBody ShippingPolicyRequest request) {
		return ApiResponse.ok(shippingPolicyService.update(request));
	}
}
