package com.example.shopping.domain.support.dto;

import com.example.shopping.domain.support.entity.Faq;

public record FaqResponse(Long id, String category, String question, String answer, int sortOrder) {

	public static FaqResponse from(Faq faq) {
		return new FaqResponse(faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer(),
				faq.getSortOrder());
	}
}
