package com.example.shopping.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 결제창이 성공 주소(successUrl)로 넘겨준 값을 그대로 보낸다.
 *
 * @param orderId 주문 생성 응답의 orderNo
 * @param amount  검증용. 서버에 저장된 주문 금액과 다르면 거절한다.
 */
public record PaymentConfirmRequest(
		@NotBlank(message = "결제 키가 없습니다.")
		@Size(max = 200, message = "결제 키가 올바르지 않습니다.")
		String paymentKey,

		@NotBlank(message = "주문번호가 없습니다.")
		@Size(max = 40, message = "주문번호가 올바르지 않습니다.")
		String orderId,

		@NotNull(message = "결제 금액이 없습니다.")
		@Positive(message = "결제 금액이 올바르지 않습니다.")
		Long amount) {
}
