package com.example.shopping.domain.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryAnswerRequest(
		@NotBlank(message = "답변을 입력해 주세요.")
		@Size(max = 5000, message = "답변은 5000자 이하여야 합니다.")
		String answer) {
}
