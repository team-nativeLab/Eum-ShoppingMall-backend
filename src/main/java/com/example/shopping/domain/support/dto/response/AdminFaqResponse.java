package com.example.shopping.domain.support.dto.response;

import com.example.shopping.domain.support.entity.Faq;

/** 관리자 FAQ(#220). 숨긴 FAQ도 들어 있다. */
public record AdminFaqResponse(Long id, String category, String question, String answer, int sortOrder,
		boolean visible) {

	public static AdminFaqResponse from(Faq faq) {
		return new AdminFaqResponse(faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer(),
				faq.getSortOrder(), faq.isVisible());
	}
}
