package com.example.shopping.domain.support.dto;

import java.time.LocalDateTime;

import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;

/** memberId가 null이면 비회원 문의이고, 답변은 contactPhone·contactEmail로 전달해야 한다. */
public record AdminInquiryDetailResponse(Long id, Long memberId, String contactPhone, String contactEmail,
		String inquiryType, String title, String content, String answer, InquiryStatus status,
		LocalDateTime createdAt) {

	public static AdminInquiryDetailResponse from(Inquiry inquiry) {
		return new AdminInquiryDetailResponse(inquiry.getId(), inquiry.getMemberId(), inquiry.getContactPhone(),
				inquiry.getContactEmail(), inquiry.getInquiryType(), inquiry.getTitle(), inquiry.getContent(),
				inquiry.getAnswer(), inquiry.getStatus(), inquiry.getCreatedAt());
	}
}
