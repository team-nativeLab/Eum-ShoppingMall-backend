package com.example.shopping.domain.quote.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param productId 특정 상품(금액 미표시 상품 등)에 대한 견적이면 그 상품의 ID. 아니면 생략
 */
public record QuoteCreateRequest(
		Long productId,

		@NotBlank(message = "회사명을 입력해 주세요.")
		@Size(max = 100, message = "회사명은 100자 이하여야 합니다.")
		String companyName,

		@NotBlank(message = "담당자 이름을 입력해 주세요.")
		@Size(max = 50, message = "담당자 이름은 50자 이하여야 합니다.")
		String managerName,

		@NotBlank(message = "연락처를 입력해 주세요.")
		@Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
		String phone,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		@NotBlank(message = "문의 내용을 입력해 주세요.")
		@Size(max = 5000, message = "문의 내용은 5000자 이하여야 합니다.")
		String content) {
}
