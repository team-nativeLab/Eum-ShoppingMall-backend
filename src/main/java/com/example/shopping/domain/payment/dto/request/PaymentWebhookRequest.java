package com.example.shopping.domain.payment.dto.request;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 토스페이먼츠 웹훅. 신호로만 쓰고, 실제 결제 상태는 토스페이먼츠에 조회해 확인한다.
 *
 * @param eventType 이벤트 종류. PAYMENT_STATUS_CHANGED와 DEPOSIT_CALLBACK만 처리하고 나머지는 무시한다.
 * @param data      이벤트 내용
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentWebhookRequest(@NotBlank String eventType, @NotNull @Valid Data data) {

	/**
	 * 웹훅 이벤트 내용
	 *
	 * @param orderId        주문번호(orderNo)
	 * @param paymentKey     결제 키
	 * @param status         결제 상태(토스페이먼츠 값)
	 * @param secret         가상계좌를 발급할 때 받은 비밀값. 입금 알림이 진짜인지 확인한다.
	 * @param transactionKey 거래 키
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Data(String orderId, String paymentKey, String status, String secret, String transactionKey) {
		public boolean hasVerificationFields() {
			return StringUtils.hasText(orderId) && StringUtils.hasText(secret);
		}
	}
}
