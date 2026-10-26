package com.example.shopping.domain.support.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.notification.entity.NotificationMessage;
import com.example.shopping.domain.notification.entity.NotificationStatus;
import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;

/**
 * memberId가 null이면 비회원 문의다. 답변을 등록하면 연락처(없으면 회원 휴대폰 번호)로 알림톡을 보낸다.
 *
 * @param answerNotification 가장 최근 답변 알림의 발송 상태. 보낸 적이 없으면(휴대폰 번호가 없는 경우 등) null
 */
public record AdminInquiryDetailResponse(Long id, Long memberId, String contactPhone, String contactEmail,
		String inquiryType, String title, String content, String answer, InquiryStatus status,
		LocalDateTime createdAt, AnswerNotification answerNotification) {

	public static AdminInquiryDetailResponse from(Inquiry inquiry, NotificationMessage latestNotification) {
		return new AdminInquiryDetailResponse(inquiry.getId(), inquiry.getMemberId(), inquiry.getContactPhone(),
				inquiry.getContactEmail(), inquiry.getInquiryType(), inquiry.getTitle(), inquiry.getContent(),
				inquiry.getAnswer(), inquiry.getStatus(), inquiry.getCreatedAt(),
				latestNotification == null ? null : AnswerNotification.from(latestNotification));
	}

	/**
	 * @param status    PENDING(보내는 중·다시 보낼 예정), SENT, FAILED(3번 실패), SKIPPED(알림톡 대행사 미설정)
	 * @param lastError 마지막 실패 사유
	 */
	public record AnswerNotification(NotificationStatus status, int attempts, LocalDateTime sentAt,
			String lastError) {

		static AnswerNotification from(NotificationMessage message) {
			return new AnswerNotification(message.getStatus(), message.getAttempts(), message.getSentAt(),
					message.getLastError());
		}
	}
}
