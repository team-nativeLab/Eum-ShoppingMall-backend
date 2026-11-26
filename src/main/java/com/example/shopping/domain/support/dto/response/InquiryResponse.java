package com.example.shopping.domain.support.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;

/**
 * answer는 답변 전이면 null
 *
 * @param id          문의 ID
 * @param inquiryType 문의 유형
 * @param title       제목
 * @param content     내용
 * @param answer      답변. 답변 전이면 null
 * @param status      처리 상태. RECEIVED(접수), ANSWERED(답변 완료)
 * @param createdAt   문의 시각
 */
public record InquiryResponse(Long id, String inquiryType, String title, String content, String answer,
		InquiryStatus status, LocalDateTime createdAt) {

	public static InquiryResponse from(Inquiry inquiry) {
		return new InquiryResponse(inquiry.getId(), inquiry.getInquiryType(), inquiry.getTitle(),
				inquiry.getContent(), inquiry.getAnswer(), inquiry.getStatus(), inquiry.getCreatedAt());
	}
}
