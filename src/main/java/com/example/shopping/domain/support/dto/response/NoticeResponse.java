package com.example.shopping.domain.support.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.support.entity.Notice;

/**
 * content는 상세 조회에서만 채운다 (목록에서는 null).
 *
 * @param id        공지 ID
 * @param title     제목
 * @param content   내용. 목록에서는 null
 * @param pinned    상단 고정
 * @param createdAt 등록 시각
 */
public record NoticeResponse(Long id, String title, String content, boolean pinned, LocalDateTime createdAt) {

	public static NoticeResponse summary(Notice notice) {
		return new NoticeResponse(notice.getId(), notice.getTitle(), null, notice.isPinned(), notice.getCreatedAt());
	}

	public static NoticeResponse detail(Notice notice) {
		return new NoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(), notice.isPinned(),
				notice.getCreatedAt());
	}
}
