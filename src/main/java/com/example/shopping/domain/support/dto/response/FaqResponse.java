package com.example.shopping.domain.support.dto.response;

import com.example.shopping.domain.support.entity.Faq;

/**
 * FAQ
 *
 * @param id        FAQ ID
 * @param category  분류
 * @param question  질문
 * @param answer    답변
 * @param sortOrder 정렬 순서. 작을수록 앞
 */
public record FaqResponse(Long id, String category, String question, String answer, int sortOrder) {

	public static FaqResponse from(Faq faq) {
		return new FaqResponse(faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer(),
				faq.getSortOrder());
	}
}
