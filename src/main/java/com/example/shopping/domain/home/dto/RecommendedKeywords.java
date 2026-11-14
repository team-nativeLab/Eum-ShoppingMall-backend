package com.example.shopping.domain.home.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 검색 결과가 없을 때 보여 줄 추천 검색어. 관리자가 통째로 저장하고(PUT /api/v1/admin/search/recommended-keywords)
 * 이 모양 그대로 JSON으로 저장한다(site_setting).
 *
 * @param keywords 보여 줄 순서대로 10개까지. 앞뒤 공백을 지우고, 겹치면 처음 것만 남긴다.
 */
public record RecommendedKeywords(
		@NotNull(message = "추천 검색어 목록을 보내 주세요(없으면 빈 배열).")
		@Size(max = 10, message = "추천 검색어는 10개까지입니다.")
		List<@NotBlank(message = "빈 검색어는 넣을 수 없습니다.") @Size(max = 30, message = "검색어는 30자 이하여야 합니다.") String> keywords) {

	public RecommendedKeywords {
		if (keywords != null) {
			// null 항목은 그대로 두어 검증(@NotBlank)이 거절하게 한다.
			keywords = keywords.stream()
					.map(keyword -> keyword == null ? null : keyword.trim())
					.distinct()
					.toList();
		}
	}

	public static RecommendedKeywords empty() {
		return new RecommendedKeywords(List.of());
	}
}
