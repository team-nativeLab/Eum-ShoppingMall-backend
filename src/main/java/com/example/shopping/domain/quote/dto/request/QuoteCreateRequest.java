package com.example.shopping.domain.quote.dto.request;

import com.example.shopping.global.validation.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 견적 문의 접수. 비회원도 보낼 수 있다.
 *
 * @param productId     특정 상품(금액 미표시 상품 등)에 대한 견적이면 그 상품의 ID. 아니면 생략
 * @param companyName   회사명(100자 이하)
 * @param managerName   담당자 이름(50자 이하)
 * @param phone         연락처(예: 010-1234-5678, 하이픈은 생략 가능)
 * @param email         이메일(선택)
 * @param content       문의 내용(5,000자 이하)
 * @param privacyAgreed 개인정보 수집·이용 동의. 비회원은 true여야 한다.
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
		@Pattern(regexp = ValidationPatterns.PHONE, message = "연락처 형식이 올바르지 않습니다.")
		String phone,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		@NotBlank(message = "문의 내용을 입력해 주세요.")
		@Size(max = 5000, message = "문의 내용은 5000자 이하여야 합니다.")
		String content,
		// 개인정보 수집·이용 동의. 비회원은 필수다(#21).
		Boolean privacyAgreed) {
}
