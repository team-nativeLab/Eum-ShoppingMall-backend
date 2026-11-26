package com.example.shopping.domain.support.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;

/**
 * memberId가 null이면 비회원 문의. 목록에는 연락처와 본문을 싣지 않는다.
 *
 * @param id          문의 ID
 * @param memberId    문의한 회원 ID. 비회원이면 null
 * @param inquiryType 문의 유형
 * @param title       제목
 * @param status      처리 상태. RECEIVED(접수), ANSWERED(답변 완료)
 * @param createdAt   문의 시각
 */
public record AdminInquirySummaryResponse(Long id, Long memberId, String inquiryType, String title,
		InquiryStatus status, LocalDateTime createdAt) {

	public static AdminInquirySummaryResponse from(Inquiry inquiry) {
		return new AdminInquirySummaryResponse(inquiry.getId(), inquiry.getMemberId(), inquiry.getInquiryType(),
				inquiry.getTitle(), inquiry.getStatus(), inquiry.getCreatedAt());
	}
}
