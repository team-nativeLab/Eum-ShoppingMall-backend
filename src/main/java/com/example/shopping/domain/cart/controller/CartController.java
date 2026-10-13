package com.example.shopping.domain.cart.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.cart.dto.CartItemAddRequest;
import com.example.shopping.domain.cart.dto.CartItemUpdateRequest;
import com.example.shopping.domain.cart.dto.CartResponse;
import com.example.shopping.domain.cart.service.CartService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.security.CurrentMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 변경 API도 바뀐 장바구니 전체를 돌려주므로 프론트는 합계를 다시 계산하지 않아도 된다. */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

	private final CartService cartService;

	/** WEB-012, 013, 014 장바구니 조회 (금액 미표시 혼합·주문 가능 0 판정 포함) */
	@GetMapping
	public ApiResponse<CartResponse> cart(@AuthenticationPrincipal Jwt jwt) {
		return ApiResponse.ok(cartService.getCart(CurrentMember.id(jwt)));
	}

	@PostMapping("/items")
	public ApiResponse<CartResponse> addItem(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CartItemAddRequest request) {
		return ApiResponse.ok(cartService.addItem(CurrentMember.id(jwt), request));
	}

	/** WEB-012 수량·선택 여부 변경 */
	@PatchMapping("/items/{skuId}")
	public ApiResponse<CartResponse> updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable Long skuId,
			@Valid @RequestBody CartItemUpdateRequest request) {
		return ApiResponse.ok(cartService.updateItem(CurrentMember.id(jwt), skuId, request));
	}

	@DeleteMapping("/items/{skuId}")
	public ApiResponse<CartResponse> removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable Long skuId) {
		return ApiResponse.ok(cartService.removeItem(CurrentMember.id(jwt), skuId));
	}
}
