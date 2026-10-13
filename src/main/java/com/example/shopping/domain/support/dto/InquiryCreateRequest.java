package com.example.shopping.domain.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 비회원은 답변을 받을 연락처(전화 또는 이메일)를 하나 이상 반드시 적어야 한다. */
public record InquiryCreateRequest(
		@NotBlank(message = "문의 유형을 선택해 주세요.")
		@Size(max = 50, message = "문의 유형은 50자 이하여야 합니다.")
		String inquiryType,

		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.")
		String title,

		@NotBlank(message = "내용을 입력해 주세요.")
		@Size(max = 5000, message = "내용은 5000자 이하여야 합니다.")
		String content,

		@Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
		String contactPhone,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String contactEmail) {
}
