package com.example.shopping.domain.quote.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.quote.entity.QuoteInquiry;
import com.example.shopping.domain.quote.entity.QuoteStatus;

/** memberId가 null이면 비회원 접수, productId가 null이면 특정 상품과 무관한 문의 */
public record AdminQuoteResponse(Long id, Long memberId, Long productId, String companyName, String managerName,
		String phone, String email, String content, QuoteStatus status, LocalDateTime createdAt) {

	public static AdminQuoteResponse from(QuoteInquiry quote) {
		return new AdminQuoteResponse(quote.getId(), quote.getMemberId(), quote.getProductId(),
				quote.getCompanyName(), quote.getManagerName(), quote.getPhone(), quote.getEmail(),
				quote.getContent(), quote.getStatus(), quote.getCreatedAt());
	}
}
