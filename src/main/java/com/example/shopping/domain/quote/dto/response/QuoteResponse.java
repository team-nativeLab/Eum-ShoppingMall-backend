package com.example.shopping.domain.quote.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.quote.entity.QuoteInquiry;
import com.example.shopping.domain.quote.entity.QuoteStatus;

/**
 * 견적 문의
 *
 * @param id          견적 문의 ID
 * @param productId   상품 ID. 특정 상품과 무관하면 null
 * @param companyName 회사명
 * @param managerName 담당자 이름
 * @param phone       연락처
 * @param email       이메일. 없으면 null
 * @param content     문의 내용
 * @param status      처리 상태. RECEIVED(접수), IN_PROGRESS(처리 중), COMPLETED(완료)
 * @param createdAt   접수 시각
 */
public record QuoteResponse(Long id, Long productId, String companyName, String managerName, String phone,
		String email, String content, QuoteStatus status, LocalDateTime createdAt) {

	public static QuoteResponse from(QuoteInquiry quote) {
		return new QuoteResponse(quote.getId(), quote.getProductId(), quote.getCompanyName(),
				quote.getManagerName(), quote.getPhone(), quote.getEmail(), quote.getContent(), quote.getStatus(),
				quote.getCreatedAt());
	}
}
