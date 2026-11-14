package com.example.shopping.domain.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.order.dto.response.ShippingPolicyResponse;
import com.example.shopping.domain.order.service.ShippingPolicyService;
import com.example.shopping.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 상품·장바구니 화면의 배송비 안내(예: 10만 원 이상 무료배송)에 쓴다. 로그인 없이 조회한다. */
@Tag(name = "배송비 정책")
@RestController
@RequiredArgsConstructor
public class ShippingPolicyController {

	private final ShippingPolicyService shippingPolicyService;

	/** 쇼핑몰 안내용 배송비 정책(기본 배송비, 무료배송 기준) */
	@GetMapping("/api/v1/shipping-policy")
	public ApiResponse<ShippingPolicyResponse> get() {
		return ApiResponse.ok(shippingPolicyService.get());
	}
}
