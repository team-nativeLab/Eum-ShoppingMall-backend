package com.example.shopping.domain.quote.dto.request;

import com.example.shopping.domain.quote.entity.QuoteStatus;

import jakarta.validation.constraints.NotNull;

public record QuoteStatusUpdateRequest(
		@NotNull(message = "처리 상태를 입력해 주세요.")
		QuoteStatus status) {
}
