package com.example.shopping.domain.order.dto.request;

import com.example.shopping.domain.order.entity.ShippingAddress;
import com.example.shopping.global.validation.ValidationPatterns;
import com.example.shopping.global.validation.ContactFormats;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeliveryRequest(
		@NotBlank(message = "받는 분 이름을 입력해 주세요.")
		@Size(max = 50, message = "받는 분 이름은 50자 이하여야 합니다.")
		String recipient,

		@NotBlank(message = "연락처를 입력해 주세요.")
		@Pattern(regexp = ValidationPatterns.PHONE, message = "연락처 형식이 올바르지 않습니다.")
		String recipientPhone,

		@NotBlank(message = "우편번호를 입력해 주세요.")
		@Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
		String zipCode,

		@NotBlank(message = "주소를 입력해 주세요.")
		@Size(max = 200, message = "주소는 200자 이하여야 합니다.")
		String address,

		@Size(max = 200, message = "상세 주소는 200자 이하여야 합니다.")
		String addressDetail,

		@Size(max = 200, message = "배송 요청 사항은 200자 이하여야 합니다.")
		String deliveryRequest) {

	public ShippingAddress toAddress() {
		return ShippingAddress.of(recipient.trim(), ContactFormats.phone(recipientPhone), zipCode, address.trim(),
				addressDetail, deliveryRequest);
	}
}
