package com.example.shopping.domain.support.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.shopping.domain.history.dto.StatusHistoryResponse;
import com.example.shopping.domain.notification.entity.NotificationMessage;
import com.example.shopping.domain.notification.entity.NotificationStatus;
import com.example.shopping.domain.support.entity.Inquiry;
import com.example.shopping.domain.support.entity.InquiryStatus;

/**
 * memberId가 null이면 비회원 문의다. 답변을 등록하면 연락처(없으면 회원 휴대폰 번호)로 알림톡을 보낸다.
 *
 * @param id                 문의 ID
 * @param memberId           문의한 회원 ID. 비회원이면 null
 * @param contactPhone       답변 받을 연락처. 없으면 null
 * @param contactEmail       답변 받을 이메일. 없으면 null
 * @param inquiryType        문의 유형
 * @param title              제목
 * @param content            내용
 * @param answer             답변. 답변 전이면 null
 * @param status             처리 상태. RECEIVED(접수), ANSWERED(답변 완료)
 * @param createdAt          문의 시각
 * @param answerNotification 가장 최근 답변 알림의 발송 상태. 보낸 적이 없으면(휴대폰 번호가 없는 경우 등) null
 * @param adminMemo          고객에게 보이지 않는 내부 메모(#223)
 * @param statusHistory      상태가 바뀐 기록. 오래된 것부터(#223)
 */
public record AdminInquiryDetailResponse(Long id, Long memberId, String contactPhone, String contactEmail,
		String inquiryType, String title, String content, String answer, InquiryStatus status,
		LocalDateTime createdAt, AnswerNotification answerNotification, String adminMemo,
		List<StatusHistoryResponse> statusHistory) {

	public static AdminInquiryDetailResponse from(Inquiry inquiry, NotificationMessage latestNotification,
			List<StatusHistoryResponse> statusHistory) {
		return new AdminInquiryDetailResponse(inquiry.getId(), inquiry.getMemberId(), inquiry.getContactPhone(),
				inquiry.getContactEmail(), inquiry.getInquiryType(), inquiry.getTitle(), inquiry.getContent(),
				inquiry.getAnswer(), inquiry.getStatus(), inquiry.getCreatedAt(),
				latestNotification == null ? null : AnswerNotification.from(latestNotification),
				inquiry.getAdminMemo(), statusHistory);
	}

	/**
	 * 답변 알림(알림톡) 발송 상태
	 *
	 * @param status    PENDING(보내는 중·다시 보낼 예정), SENT, FAILED(3번 실패), SKIPPED(알림톡 대행사 미설정)
	 * @param attempts  보낸 횟수(실패 포함)
	 * @param sentAt    보낸 시각. 아직 보내지 못했으면 null
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
