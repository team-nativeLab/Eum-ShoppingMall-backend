package com.example.shopping.domain.home.dto;

import java.time.LocalDateTime;

import com.example.shopping.domain.home.entity.SiteContent;

public record ContentResponse(String code, String title, String body, LocalDateTime updatedAt) {

	public static ContentResponse from(SiteContent content) {
		return new ContentResponse(content.getCode(), content.getTitle(), content.getBody(), content.getUpdatedAt());
	}
}
