package com.example.shopping.domain.support.dto;

import java.time.LocalDateTime;

import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;

/** memberId가 null이면 비회원 문의. 목록에는 연락처와 본문을 싣지 않는다. */
public record AdminInquirySummaryResponse(Long id, Long memberId, String inquiryType, String title,
		InquiryStatus status, LocalDateTime createdAt) {

	public static AdminInquirySummaryResponse from(Inquiry inquiry) {
		return new AdminInquirySummaryResponse(inquiry.getId(), inquiry.getMemberId(), inquiry.getInquiryType(),
				inquiry.getTitle(), inquiry.getStatus(), inquiry.getCreatedAt());
	}
}
