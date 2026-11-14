package com.example.shopping.domain.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.order.dto.response.CheckoutOptionsResponse;
import com.example.shopping.domain.order.service.ShippingPolicyService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 주문서 화면의 선택지(누구나) */
@Tag(name = "주문서")
@RestController
@RequiredArgsConstructor
public class CheckoutController {

	private final ShippingPolicyService shippingPolicyService;

	/** 배송비·무료배송 기준(관리자 배송비 정책), 배송 요청 문구, 배송 방법, 결제 수단 */
	@GetMapping("/api/v1/checkout/options")
	public ApiResponse<CheckoutOptionsResponse> options() {
		return ApiResponse.ok(CheckoutOptionsResponse.of(shippingPolicyService.current()));
	}
}
