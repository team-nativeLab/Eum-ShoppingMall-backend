package com.example.shopping.domain.support.dto;

import java.time.LocalDateTime;

import com.example.shopping.domain.support.entity.Notice;

public record AdminNoticeResponse(Long id, String title, String content, boolean pinned, boolean visible,
		LocalDateTime createdAt, LocalDateTime updatedAt) {

	public static AdminNoticeResponse from(Notice notice) {
		return new AdminNoticeResponse(notice.getId(), notice.getTitle(), notice.getContent(), notice.isPinned(),
				notice.isVisible(), notice.getCreatedAt(), notice.getUpdatedAt());
	}
}
