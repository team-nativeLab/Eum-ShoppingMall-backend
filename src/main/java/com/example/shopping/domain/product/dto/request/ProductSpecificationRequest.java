package com.example.shopping.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 상품 사양 표의 한 행(예: 재질 / ABS). 제조사·재질·크기·중량 등도 이 행으로 보낸다. */
public record ProductSpecificationRequest(
		@NotBlank(message = "사양 항목을 입력해 주세요.")
		@Size(max = 50, message = "사양 항목은 50자 이하여야 합니다.")
		String label,

		@NotBlank(message = "사양 값을 입력해 주세요.")
		@Size(max = 200, message = "사양 값은 200자 이하여야 합니다.")
		String value) {
}
