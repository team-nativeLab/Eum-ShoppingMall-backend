package com.example.shopping.domain.support.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.support.entity.Notice;

/**
 * 관리자 공지. 숨긴 공지도 들어 있다.
 *
 * @param id        공지 ID
 * @param title     제목
 * @param content   내용
 * @param pinned    상단 고정
 * @param visible   공개 여부
 * @param createdAt 등록 시각
 * @param updatedAt 마지막 수정 시각
 * @param sortOrder 정렬 순서. 작을수록 앞이고, 고정 공지 안팎에서 각각 정렬한다.
 */
public record AdminNoticeResponse(Long id, String title, String content, boolean pinned, boolean visible,
		LocalDateTime createdAt, LocalDateTime updatedAt, int sortOrder) {

	public static AdminNoticeResponse from(Notice notice) {
		return new AdminNoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(), notice.isPinned(),
				notice.isVisible(), notice.getCreatedAt(), notice.getUpdatedAt(), notice.getSortOrder());
	}
}
