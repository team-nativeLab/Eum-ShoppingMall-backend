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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/shipping-policy")
@RequiredArgsConstructor
public class AdminShippingPolicyController {

	private final ShippingPolicyService shippingPolicyService;

	@GetMapping
	public ApiResponse<ShippingPolicyResponse> get() {
		return ApiResponse.ok(shippingPolicyService.get());
	}

	@PutMapping
	public ApiResponse<ShippingPolicyResponse> update(@Valid @RequestBody ShippingPolicyRequest request) {
		return ApiResponse.ok(shippingPolicyService.update(request));
	}
}
