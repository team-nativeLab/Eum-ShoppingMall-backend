package com.example.shopping.domain.payment.dto;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentWebhookRequest(@NotBlank String eventType, @NotNull @Valid Data data) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Data(String orderId, String paymentKey, String status, String secret, String transactionKey) {
		public boolean hasVerificationFields() {
			return StringUtils.hasText(orderId) && StringUtils.hasText(secret);
		}
	}
}
