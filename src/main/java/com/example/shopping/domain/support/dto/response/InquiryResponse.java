package com.example.shopping.domain.support.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;

/** answer는 답변 전이면 null */
public record InquiryResponse(Long id, String inquiryType, String title, String content, String answer,
		InquiryStatus status, LocalDateTime createdAt) {

	public static InquiryResponse from(Inquiry inquiry) {
		return new InquiryResponse(inquiry.getId(), inquiry.getInquiryType(), inquiry.getTitle(),
				inquiry.getContent(), inquiry.getAnswer(), inquiry.getStatus(), inquiry.getCreatedAt());
	}
}
