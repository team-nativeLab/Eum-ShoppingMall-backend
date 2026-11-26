package com.example.shopping.domain.support.dto.response;

import com.example.shopping.domain.support.entity.Faq;

/**
 * 관리자 FAQ(#220). 숨긴 FAQ도 들어 있다.
 *
 * @param id        FAQ ID
 * @param category  분류
 * @param question  질문
 * @param answer    답변
 * @param sortOrder 정렬 순서. 작을수록 앞
 * @param visible   고객 FAQ 목록에 보이는지
 */
public record AdminFaqResponse(Long id, String category, String question, String answer, int sortOrder,
		boolean visible) {

	public static AdminFaqResponse from(Faq faq) {
		return new AdminFaqResponse(faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer(),
				faq.getSortOrder(), faq.isVisible());
	}
}
