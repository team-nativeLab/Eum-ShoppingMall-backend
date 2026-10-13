package com.example.shopping.domain.order.dto;

import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 보낸 항목만 처리한다.
 *
 * @param status 바꿀 주문 상태. PREPARING(상품 준비중), SHIPPED(배송중), DELIVERED(배송완료)만 지정할 수 있다.
 */
public record AdminOrderUpdateRequest(
		OrderStatus status,

		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "택배사를 입력해 주세요.")
		@Size(max = 50, message = "택배사는 50자 이하여야 합니다.")
		String courier,

		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "송장번호를 입력해 주세요.")
		@Size(max = 50, message = "송장번호는 50자 이하여야 합니다.")
		String trackingNumber) {
}
