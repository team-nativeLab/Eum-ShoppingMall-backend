package com.example.shopping.domain.home.dto.response;

import java.time.LocalDateTime;

import com.example.shopping.domain.home.entity.SiteContent;

/**
 * 고정 콘텐츠(회사소개, 이용약관, 개인정보 처리방침 본문)
 *
 * @param code      콘텐츠 코드(company, terms, privacy-policy)
 * @param title     제목
 * @param body      평문 본문
 * @param updatedAt 마지막 수정 시각
 */
public record ContentResponse(String code, String title, String body, LocalDateTime updatedAt) {

	public static ContentResponse from(SiteContent content) {
		return new ContentResponse(content.getCode(), content.getTitle(), content.getBody(), content.getUpdatedAt());
	}
}
