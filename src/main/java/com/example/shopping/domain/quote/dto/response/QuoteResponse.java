package com.example.shopping.domain.quote.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.quote.entity.QuoteInquiry;
import com.example.shopping.domain.quote.entity.QuoteStatus;

public record QuoteResponse(Long id, Long productId, String companyName, String managerName, String phone,
		String email, String content, QuoteStatus status, LocalDateTime createdAt) {

	public static QuoteResponse from(QuoteInquiry quote) {
		return new QuoteResponse(quote.getId(), quote.getProductId(), quote.getCompanyName(),
				quote.getManagerName(), quote.getPhone(), quote.getEmail(), quote.getContent(), quote.getStatus(),
				quote.getCreatedAt());
	}
}
