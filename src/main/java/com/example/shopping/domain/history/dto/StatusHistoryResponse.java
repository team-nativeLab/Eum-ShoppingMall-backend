package com.example.shopping.domain.history.dto;

import java.time.LocalDateTime;

import com.example.shopping.domain.history.entity.StatusHistory;

/**
 * 상태 변경 이력 한 줄(#223)
 *
 * @param status     바뀐 뒤 상태
 * @param occurredAt 바뀐 시각
 * @param note       바뀐 까닭(예: 결제 완료, 관리자 상태 변경)
 */
public record StatusHistoryResponse(String status, LocalDateTime occurredAt, String note) {

	public static StatusHistoryResponse from(StatusHistory history) {
		return new StatusHistoryResponse(history.getStatus(), history.getOccurredAt(), history.getNote());
	}
}
