package com.example.shopping.domain.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shopping.domain.order.dto.OrderDetailResponse;
import com.example.shopping.domain.payment.dto.PaymentConfirmRequest;
import com.example.shopping.domain.payment.dto.PaymentWebhookRequest;
import com.example.shopping.domain.payment.service.PaymentService;
import com.example.shopping.global.response.ApiResponse;
import com.example.shopping.global.security.CurrentMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	/**
	 * WEB-016 결제 승인. 결제된 주문의 상세를 돌려준다.
	 * 응답 코드가 PAYMENT_PENDING_CONFIRMATION(202)이면 결과 확인 중이므로 주문 상세를 다시 조회해 상태를 확인한다.
	 */
	@PostMapping("/confirm")
	public ApiResponse<OrderDetailResponse> confirm(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PaymentConfirmRequest request) {
		return ApiResponse.ok(paymentService.confirm(CurrentMember.id(jwt), request));
	}

	/** Toss 가상계좌 웹훅. 본문은 신호로만 사용하고 PG 조회 결과를 검증해 반영한다. */
	@PostMapping("/webhook")
	public ResponseEntity<Void> webhook(@Valid @RequestBody PaymentWebhookRequest request) {
		paymentService.handleWebhook(request);
		return ResponseEntity.ok().build();
	}
}
