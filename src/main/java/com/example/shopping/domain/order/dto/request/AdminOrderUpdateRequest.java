package com.example.shopping.domain.order.dto.request;

import com.example.shopping.domain.order.entity.OrderStatus;
import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 보낸 항목만 처리한다.
 *
 * @param status    바꿀 주문 상태. PREPARING(상품 준비중), SHIPPED(배송중), DELIVERED(배송완료)만 지정할 수 있다.
 *                  SHIPPED는 택배사와 송장번호가 있어야 한다(이미 등록했거나 같은 요청에 보낸다).
 * @param adminMemo 고객에게 보이지 않는 내부 메모(#223). 빈 문자열이면 지운다.
 */
public record AdminOrderUpdateRequest(
		OrderStatus status,

		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "택배사를 입력해 주세요.")
		@Size(max = 50, message = "택배사는 50자 이하여야 합니다.")
		String courier,

		@Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "송장번호를 입력해 주세요.")
		@Size(max = 50, message = "송장번호는 50자 이하여야 합니다.")
		String trackingNumber,

		@Size(max = 2000, message = "메모는 2,000자 이하여야 합니다.")
		String adminMemo) {
}
